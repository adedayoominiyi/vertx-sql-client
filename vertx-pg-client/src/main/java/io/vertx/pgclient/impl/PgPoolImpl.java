/*
 * Copyright (C) 2017 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.vertx.pgclient.impl;

import io.vertx.core.impl.ContextInternal;
import io.vertx.pgclient.*;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.impl.Connection;
import io.vertx.sqlclient.impl.PoolBase;
import io.vertx.sqlclient.impl.SqlConnectionImpl;
import io.vertx.core.*;

/**
 * Todo :
 *
 * - handle timeout when acquiring a connection
 * - for per statement pooling, have several physical connection and use the less busy one to avoid head of line blocking effect
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */
public class PgPoolImpl extends PoolBase<PgPoolImpl> implements PgPool {

  private final PgConnectionFactory factory;
  private final ContextInternal contextHook;
  private final Closeable hook;

  public PgPoolImpl(ContextInternal context, boolean closeVertx, PgConnectOptions connectOptions, PoolOptions poolOptions) {
    super(context, closeVertx, poolOptions);
    this.factory = new PgConnectionFactory(context.owner(), connectOptions);

    if (context.deploymentID() != null) {
      contextHook = context;
      hook = completion -> {
        closeInternal();
        completion.complete();
      };
      context.addCloseHook(hook);
    } else {
      contextHook = null;
      hook = null;
    }
  }

  @Override
  public void connect(ContextInternal context, Handler<AsyncResult<Connection>> completionHandler) {
    factory.connectAndInit(context).setHandler(completionHandler);
  }

  @Override
  protected SqlConnectionImpl wrap(ContextInternal context, Connection conn) {
    return new PgConnectionImpl(factory, context, conn);
  }

  @Override
  protected void doClose() {
    if (hook != null) {
      contextHook.removeCloseHook(hook);
    }
    closeInternal();
  }

  private void closeInternal() {
    factory.close();
    super.doClose();
  }
}

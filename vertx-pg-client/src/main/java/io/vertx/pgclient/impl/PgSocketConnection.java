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

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.pgclient.impl.codec.PgCodec;
import io.vertx.sqlclient.impl.Connection;
import io.vertx.sqlclient.impl.SocketConnectionBase;
import io.vertx.sqlclient.impl.command.InitCommand;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.NetSocketInternal;

import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class PgSocketConnection extends SocketConnectionBase {

  private PgCodec codec;
  public int processId;
  public int secretKey;

  public PgSocketConnection(NetSocketInternal socket,
                            boolean cachePreparedStatements,
                            int preparedStatementCacheSize,
                            int preparedStatementCacheSqlLimit,
                            int pipeliningLimit,
                            ContextInternal context) {
    super(socket, cachePreparedStatements, preparedStatementCacheSize, preparedStatementCacheSqlLimit, pipeliningLimit, context);
  }

  @Override
  public void init() {
    codec = new PgCodec();
    ChannelPipeline pipeline = socket.channelHandlerContext().pipeline();
    pipeline.addBefore("handler", "codec", codec);
    super.init();
  }

  void sendStartupMessage(String username, String password, String database, Map<String, String> properties, Promise<Connection> completionHandler) {
    InitCommand cmd = new InitCommand(this, username, password, database, properties);
    schedule(cmd, completionHandler);
  }

  void sendCancelRequestMessage(int processId, int secretKey, Handler<AsyncResult<Void>> handler) {
    Buffer buffer = Buffer.buffer(16);
    buffer.appendInt(16);
    // cancel request code
    buffer.appendInt(80877102);
    buffer.appendInt(processId);
    buffer.appendInt(secretKey);

    socket.write(buffer, ar -> {
      if (ar.succeeded()) {
        // directly close this connection
        if (status == Status.CONNECTED) {
          status = Status.CLOSING;
          socket.close();
        }
        handler.handle(Future.succeededFuture());
      } else {
        handler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  @Override
  public int getProcessId() {
    return processId;
  }

  @Override
  public int getSecretKey() {
    return secretKey;
  }

  void upgradeToSSLConnection(Handler<AsyncResult<Void>> completionHandler) {
    ChannelPipeline pipeline = socket.channelHandlerContext().pipeline();
    Promise<Void> upgradePromise = Promise.promise();
    upgradePromise.future().setHandler(ar->{
      if (ar.succeeded()) {
        completionHandler.handle(Future.succeededFuture());
      } else {
        Throwable cause = ar.cause();
        if (cause instanceof DecoderException) {
          DecoderException err = (DecoderException) cause;
          cause = err.getCause();
        }
        completionHandler.handle(Future.failedFuture(cause));
      }
    });
    pipeline.addBefore("handler", "initiate-ssl-handler", new InitiateSslHandler(this, upgradePromise));
  }

}

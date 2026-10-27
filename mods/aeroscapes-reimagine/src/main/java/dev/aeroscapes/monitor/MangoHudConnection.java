package dev.aeroscapes.monitor;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A bounded, asynchronous caller's connection to MangoHud 0.8.4's Linux control socket. */
public final class MangoHudConnection {
  private static final int TIMEOUT_SECONDS = 3;
  private static final String HELLO = ":MangoHudControlVersion=1;";

  private MangoHudConnection() {}

  public static void send(String socketName, String message)
      throws IOException, InterruptedException {
    if (!Epoll.isAvailable())
      throw new IOException("Netty epoll is unavailable", Epoll.unavailabilityCause());
    var group =
        new EpollEventLoopGroup(
            1,
            task -> {
              Thread thread = new Thread(task, "aeroscapes-monitor-netty");
              thread.setDaemon(true);
              return thread;
            });
    Channel channel = null;
    try {
      var handshake = new Greeting();
      var bootstrap =
          new Bootstrap()
              .group(group)
              .channel(EpollDomainSocketChannel.class)
              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_SECONDS * 1000)
              .handler(handshake);
      ChannelFuture connection = bootstrap.connect(new DomainSocketAddress("\0" + socketName));
      channel = connection.channel();
      if (!connection.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) || !connection.isSuccess()) {
        throw new IOException("MangoHud socket unavailable", connection.cause());
      }
      if (!handshake.ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) || !handshake.valid) {
        throw new IOException("MangoHud control handshake unavailable");
      }
      ChannelFuture write =
          channel.writeAndFlush(Unpooled.copiedBuffer(message, StandardCharsets.US_ASCII));
      if (!write.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) || !write.isSuccess()) {
        throw new IOException("MangoHud control request could not be sent", write.cause());
      }
    } finally {
      if (channel != null) channel.close().awaitUninterruptibly(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      group.shutdownGracefully(0, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
  }

  private static final class Greeting extends ChannelInboundHandlerAdapter {
    private final CountDownLatch ready = new CountDownLatch(1);
    private final StringBuilder response = new StringBuilder();
    private volatile boolean valid;

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
      if (!(message instanceof ByteBuf buffer)) {
        context.fireChannelRead(message);
        return;
      }
      try {
        response.append(buffer.toString(CharsetUtil.US_ASCII));
        if (response.indexOf(HELLO) >= 0) {
          valid = true;
          ready.countDown();
        } else if (response.length() > 4096) {
          ready.countDown();
          context.close();
        }
      } finally {
        buffer.release();
      }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
      ready.countDown();
      context.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
      ready.countDown();
      context.close();
    }
  }
}

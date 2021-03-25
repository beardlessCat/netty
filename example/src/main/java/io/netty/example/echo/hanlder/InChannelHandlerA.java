package io.netty.example.echo.hanlder;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class InChannelHandlerA extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("InChannelHandlerA——channelRead()");
        ctx.fireChannelRead(msg);
        ctx.pipeline().writeAndFlush(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
       ctx.pipeline().fireChannelRead("hello");
    }
}

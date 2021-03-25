package io.netty.example.echo;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.example.echo.hanlder.*;

public class UserChannelInitializer extends ChannelInitializer {
    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast("A-IN",new InChannelHandlerA());
        ch.pipeline().addLast("B-IN",new InChannelHandlerB());
        ch.pipeline().addLast("C-IN",new InChannelHandlerC());
    }
}

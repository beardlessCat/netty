/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Echoes back any received data from a client.
 */
public final class Server {

    /*
     *netty中启动服务的流程
     *
     * //【1】 netty 中使用 NioEventLoopGroup （简称 nio boss 线程）来封装线程和 selector
     * Selector selector = Selector.open();
     *
     * //【2】 创建 NioServerSocketChannel，同时会初始化它关联的 handler，以及为原生 ssc 存储 config
     * NioServerSocketChannel attachment = new NioServerSocketChannel();
     *
     * //【3】 创建了 java 原生的 ServerSocketChannel
     * ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
     *
     * //【4】设置为非堵塞
     * serverSocketChannel.configureBlocking(false);
     *
     * //【5】 启动 nio boss 线程执行接下来的操作
     *
     * //【6】 注册（仅关联 selector 和 NioServerSocketChannel），未关注事件
     * SelectionKey selectionKey = serverSocketChannel.register(selector, 0, attachment);
     *
     * //【7】 head -> 初始化器 -> ServerBootstrapAcceptor -> tail，初始化器是一次性的，只为添加 acceptor
     *
     * //【8】 绑定端口
     * serverSocketChannel.bind(new InetSocketAddress(8080));
     *
     * //【9】 触发 channel active 事件，在 head 中关注 op_accept 事件
     * selectionKey.interestOps(SelectionKey.OP_ACCEPT);
     */




    public static void main(String[] args) throws Exception {
        //初始化各种组件
        //reactor主从模式，创建了两个EventLoopGroup
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            //校验bossGroup和workerGroup并设置为ServerBootStrap中的group和childGroup。
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new LoggingHandler(LogLevel.INFO));
                        p.addLast(serverHandler);
                    }
                });

            // Start the server.
            ChannelFuture f = b.bind(8080).sync();
            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }
}

/*
 * Copyright 2013 by Maxim Kalina
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.javaforge.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultEventExecutor;
import net.javaforge.netty.servlet.bridge.ServletBridgeHandler;
import net.javaforge.netty.servlet.bridge.config.ServletConfiguration;
import net.javaforge.netty.servlet.bridge.config.WebappConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.javaforge.netty.servlet.bridge.impl.ServletBridgeWebapp;
import net.javaforge.netty.servlet.bridge.interceptor.ChannelInterceptor;
import net.javaforge.netty.servlet.bridge.interceptor.HttpSessionInterceptor;
import net.javaforge.netty.servlet.bridge.session.DefaultServletBridgeHttpSessionStore;
import net.javaforge.netty.servlet.bridge.session.ServletBridgeHttpSessionStore;
import net.javaforge.netty.vaadin.Servlet;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {

        long start = System.currentTimeMillis();

        final EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        // Web application configuration
        WebappConfiguration webapp = new WebappConfiguration();
        webapp.addServletConfigurations(new ServletConfiguration(
                Servlet.class, "/*"));

        // Configure the server.
        ServerBootstrap b = new ServerBootstrap(); // (2)
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class) // (3)
                .childHandler(new ServletHandler(webapp))
                .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

        try {
            // Bind and start to accept incoming connections.
            Channel ch = b.bind(8080).sync().channel();
            long end = System.currentTimeMillis();
            log.info(">>> Server started in {} ms .... <<< ", (end - start));
            ch.closeFuture().sync();
        } catch (InterruptedException ex) {
            log.error("bind :8080 failed", ex);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                //servletBridge.shutdown();
                bossGroup.shutdownGracefully();
            }
        });

    }

    /**
     * TODO: This is to replace ServletBridgeChannelPipelineFactory
     */
    public static class ServletHandler extends ChannelInitializer<SocketChannel> {

        private final HashedWheelTimer timer;
        private final ChannelGroup allChannels;
        private final DefaultEventExecutor eventExecutor;

        public ServletHandler(WebappConfiguration config) {
            this.eventExecutor = new DefaultEventExecutor();
            this.allChannels = new DefaultChannelGroup(eventExecutor);
            this.timer = new HashedWheelTimer();

            ServletBridgeWebapp webapp = ServletBridgeWebapp.get();
            webapp.init(config, allChannels);


        }

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(1048576));

            // Remove the following line if you don't want automatic content
            // compression.
            pipeline.addLast("deflater", new HttpContentCompressor());
            pipeline.addLast("idle", new IdleStateHandler(60, 30, 0));
            pipeline.addLast(getServletBridgeHandler());
        }

        protected ServletBridgeHandler getServletBridgeHandler() {
            ServletBridgeHandler bridge = new ServletBridgeHandler();
            bridge.addInterceptor(new ChannelInterceptor());
            bridge.addInterceptor(new HttpSessionInterceptor(
                    new DefaultServletBridgeHttpSessionStore()));
            return bridge;
        }
    }

}

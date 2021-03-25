package io.netty.example.echo;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.RecvByteBufAllocator;

public class AdapterTest {
    static  ByteBufAllocator byteBufAllocator = ByteBufAllocator.DEFAULT;
    static  DefaultChannelConfig defaultChannelConfig = null;
    static  AdaptiveRecvByteBufAllocator adaptiveRecvByteBufAllocator = new AdaptiveRecvByteBufAllocator();
    static RecvByteBufAllocator.Handle handle = adaptiveRecvByteBufAllocator.newHandle();

    public static void main(String[] args) {
        testDecrease(handle);
        testIncrease(handle);
    }

    /**
     * 测试自动增长容量
     * @param handle
     */
    private static void testIncrease( RecvByteBufAllocator.Handle handle) {
        allocateByteBuf(2049);
        ByteBuf buf = null;
        try {
            buf = handle.allocate(byteBufAllocator);
            System.out.println(buf.capacity());
        }finally {
            buf.release();
        }
    }

    /**
     * 测试自动缩小容量
     * @param handle
     */
    private static void testDecrease(RecvByteBufAllocator.Handle handle) {
        allocateByteBuf(1023);
        allocateByteBuf(1023);
        ByteBuf buf = null;
        try {
            buf = handle.allocate(byteBufAllocator);
            System.out.println(buf.capacity());
        }finally {
            buf.release();
        }
    }
    //分配内存
    private static void allocateByteBuf(int size) {
        ByteBuf buf = null;
        try {
            buf = handle.allocate(byteBufAllocator);
            handle.attemptedBytesRead(buf.writableBytes());
            handle.lastBytesRead(size);
            handle.readComplete();
            handle.reset(defaultChannelConfig);
            System.out.println(buf.capacity());
        } finally {
            buf.release();
        }
    }
}

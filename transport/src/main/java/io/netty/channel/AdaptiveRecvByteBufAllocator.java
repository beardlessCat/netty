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
package io.netty.channel;

import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * The {@link RecvByteBufAllocator} that automatically increases and
 * decreases the predicted buffer size on feed back.
 * <p>
 * It gradually increases the expected number of readable bytes if the previous
 * read fully filled the allocated buffer.  It gradually decreases the expected
 * number of readable bytes if the read operation was not able to fill a certain
 * amount of the allocated buffer two times consecutively.  Otherwise, it keeps
 * returning the same prediction.
 */
public class AdaptiveRecvByteBufAllocator extends DefaultMaxMessagesRecvByteBufAllocator {
    // 默认缓冲区的最小容量大小为64
    static final int DEFAULT_MINIMUM = 64;
    // Use an initial value that is bigger than the common MTU of 1500
    // 默认缓冲区的容量大小为2048
    static final int DEFAULT_INITIAL = 2048;
    // 默认缓冲区的最大容量大小为65536
    static final int DEFAULT_MAXIMUM = 65536;
    // 在调整缓冲区大小时，若是增加缓冲区容量，那么增加的索引值。
    // 比如，当前缓冲区的大小为SIZE_TABLE[20],若预测下次需要创建的缓冲区需要增加容量大小，
    // 则新缓冲区的大小为SIZE_TABLE[20 + INDEX_INCREMENT]，即SIZE_TABLE[24]
    private static final int INDEX_INCREMENT = 4;
    // 在调整缓冲区大小时，若是减少缓冲区容量，那么减少的索引值。
    // 比如，当前缓冲区的大小为SIZE_TABLE[20],若预测下次需要创建的缓冲区需要减小容量大小，
    // 则新缓冲区的大小为SIZE_TABLE[20 - INDEX_DECREMENT]，即SIZE_TABLE[19]
    private static final int INDEX_DECREMENT = 1;

    // 用于存储缓冲区容量大小的数组
    private static final int[] SIZE_TABLE;

    static {
        List<Integer> sizeTable = new ArrayList<Integer>();
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }

        // Suppress a warning since i becomes negative when an integer overflow happens
        for (int i = 512; i > 0; i <<= 1) { // lgtm[java/constant-comparison]
            sizeTable.add(i);
        }

        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i ++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
    }

    /**
     * @deprecated There is state for {@link #maxMessagesPerRead()} which is typically based upon channel type.
     */
    @Deprecated
    public static final AdaptiveRecvByteBufAllocator DEFAULT = new AdaptiveRecvByteBufAllocator();

    /**
     * 因为SIZE_TABLE数组是一个有序数组，因此此处用二分查找法，查找size在SIZE_TABLE中的位置，如果size存在于SIZE_TABLE中，则返回对应的索引值；否则返回接近于size大小的SIZE_TABLE数组元素的索引值。
     *
     * @param size
     * @return
     */
    private static int getSizeTableIndex(final int size) {
        for (int low = 0, high = SIZE_TABLE.length - 1;;) {
            if (high < low) {
                return low;
            }
            if (high == low) {
                return high;
            }

            int mid = low + high >>> 1;
            int a = SIZE_TABLE[mid];
            int b = SIZE_TABLE[mid + 1];
            if (size > b) {
                low = mid + 1;
            } else if (size < a) {
                high = mid - 1;
            } else if (size == a) {
                return mid;
            } else {
                return mid + 1;
            }
        }
    }
    //HandleImpl是AdaptiveRecvByteBufAllocator一个内部类，该处理器类用于提供真实的操作并保留预测最佳缓冲区容量所需的内部信息。
    private final class HandleImpl extends MaxMessageHandle {
        // 缓冲区最小容量对应于SIZE_TABLE中的下标位置，同外部类AdaptiveRecvByteBufAllocator是一个值
        private final int minIndex;
        // 缓冲区最大容量对应于SIZE_TABLE中的下标位置，同外部类AdaptiveRecvByteBufAllocator是一个值
        private final int maxIndex;
        // 缓冲区默认容量对应于SIZE_TABLE中的下标位置，外部类AdaptiveRecvByteBufAllocator记录的是容量大小值，而HandleImpl中记录是其值对应于SIZE_TABLE中的下标位置
        private int index;
        // 下一次创建缓冲区时的其容量的大小。
        private int nextReceiveBufferSize;
        // 在record()方法中使用，用于标识是否需要减少下一次创建的缓冲区的大小。
        private boolean decreaseNow;

        HandleImpl(int minIndex, int maxIndex, int initial) {
            this.minIndex = minIndex;
            this.maxIndex = maxIndex;

            index = getSizeTableIndex(initial);
            nextReceiveBufferSize = SIZE_TABLE[index];
        }

        @Override
        public void lastBytesRead(int bytes) {
            // If we read as much as we asked for we should check if we need to ramp up the size of our next guess.
            // This helps adjust more quickly when large amounts of data is pending and can avoid going back to
            // the selector to check for more data. Going back to the selector can add significant latency for large
            // data transfers.


            if (bytes == attemptedBytesRead()) {
                record(bytes);
            }
            super.lastBytesRead(bytes);
        }

        @Override
        public int guess() {
            return nextReceiveBufferSize;
        }

        //举个例子：16,32,48,64,80,96一共6个尺寸，开始是在32，如果两次发现真实的读取数据都小于等于16，那就设置成16，
        // 如果发现数据大于等于32，就跳INDEX_INCREMENT(4)个位置，就是96。

        private void record(int actualReadBytes) {
            //【可能需要缩容】实际的长度小于等于当前选择容量的索引减1，两次都比当前的容量大，才会将容量缩小为前一个索引对应的大小
            if (actualReadBytes <= SIZE_TABLE[max(0, index - INDEX_DECREMENT)]) {
                //第一次，decreaseNow为false,执行else判断，将decreaseNow设置为true。下一次还比当前容量小时，直接进行缩小。
                if (decreaseNow) {
                    index = max(index - INDEX_DECREMENT, minIndex);
                    //设置下一次的容量
                    nextReceiveBufferSize = SIZE_TABLE[index];
                    decreaseNow = false;
                } else {
                    decreaseNow = true;
                }
            //可能需要扩容
            } else if (actualReadBytes >= nextReceiveBufferSize) {
                //下一次的容量跳INDEX_INCREMENT(4)个
                index = min(index + INDEX_INCREMENT, maxIndex);
                nextReceiveBufferSize = SIZE_TABLE[index];
                //用于清除上次接受数据的长度小于当前选择容量的情况：
                decreaseNow = false;
            }
            //不需要扩容或者缩容
        }

        @Override
        public void readComplete() {
            record(totalBytesRead());
        }
    }

    private final int minIndex;
    private final int maxIndex;
    private final int initial;

    /**
     * Creates a new predictor with the default parameters.  With the default
     * parameters, the expected buffer size starts from {@code 1024}, does not
     * go down below {@code 64}, and does not go up above {@code 65536}.
     */
    public AdaptiveRecvByteBufAllocator() {
        this(DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);
    }

    /**
     * Creates a new predictor with the specified parameters.
     *
     * @param minimum  the inclusive lower bound of the expected buffer size
     * @param initial  the initial buffer size when no feed back was received
     * @param maximum  the inclusive upper bound of the expected buffer size
     */
    public AdaptiveRecvByteBufAllocator(int minimum, int initial, int maximum) {
        checkPositive(minimum, "minimum");
        if (initial < minimum) {
            throw new IllegalArgumentException("initial: " + initial);
        }
        if (maximum < initial) {
            throw new IllegalArgumentException("maximum: " + maximum);
        }

        int minIndex = getSizeTableIndex(minimum);
        if (SIZE_TABLE[minIndex] < minimum) {
            this.minIndex = minIndex + 1;
        } else {
            this.minIndex = minIndex;
        }

        int maxIndex = getSizeTableIndex(maximum);
        if (SIZE_TABLE[maxIndex] > maximum) {
            this.maxIndex = maxIndex - 1;
        } else {
            this.maxIndex = maxIndex;
        }

        this.initial = initial;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Handle newHandle() {
        return new HandleImpl(minIndex, maxIndex, initial);
    }

    @Override
    public AdaptiveRecvByteBufAllocator respectMaybeMoreData(boolean respectMaybeMoreData) {
        super.respectMaybeMoreData(respectMaybeMoreData);
        return this;
    }
}

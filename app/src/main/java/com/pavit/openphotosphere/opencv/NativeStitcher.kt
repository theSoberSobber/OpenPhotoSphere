package com.pavit.openphotosphere.opencv

object NativeStitcher {
    init {
        System.loadLibrary("opencv_java4")
        System.loadLibrary("stitcher_native")
    }

    external fun stitch(imageAddresses: LongArray, outputAddress: Long): Int
}

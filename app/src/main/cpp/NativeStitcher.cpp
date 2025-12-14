#include <jni.h>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/stitching.hpp>

using namespace cv;

extern "C"
JNIEXPORT jint JNICALL
Java_com_pavit_openphotosphere_opencv_NativeStitcher_stitch(
        JNIEnv* env,
        jobject /*thiz*/,
        jlongArray image_addresses,
        jlong output_address
) {
    const jsize len = env->GetArrayLength(image_addresses);
    if (len < 2) {
        return -1;
    }

    std::vector<Mat> images;
    images.reserve(len);

    jlong* addresses = env->GetLongArrayElements(image_addresses, nullptr);
    for (jsize i = 0; i < len; ++i) {
        Mat& src = *reinterpret_cast<Mat*>(addresses[i]);
        Mat prepared;

        // Ensure 3-channel RGB input for the stitcher.
        if (src.channels() == 4) {
            cvtColor(src, prepared, COLOR_RGBA2RGB);
        } else if (src.channels() == 1) {
            cvtColor(src, prepared, COLOR_GRAY2RGB);
        } else {
            src.copyTo(prepared);
        }

        // Downscale large inputs to reduce memory/compute pressure.
        const double targetHeight = 1000.0;
        if (prepared.rows > targetHeight) {
            const double scale = targetHeight / static_cast<double>(prepared.rows);
            const int newWidth = static_cast<int>(prepared.cols * scale);
            const int newHeight = static_cast<int>(prepared.rows * scale);
            resize(prepared, prepared, Size(newWidth, newHeight));
        }

        images.push_back(prepared);
    }

    env->ReleaseLongArrayElements(image_addresses, addresses, JNI_ABORT);

    Mat& result = *reinterpret_cast<Mat*>(output_address);

    Ptr<Stitcher> stitcher = Stitcher::create(Stitcher::PANORAMA);
    stitcher->setWaveCorrection(true);
    stitcher->setWaveCorrectKind(detail::WAVE_CORRECT_HORIZ);

    const Stitcher::Status status = stitcher->stitch(images, result);
    const auto code = static_cast<jint>(status);
    if (status != Stitcher::OK || result.empty()) {
        return code;
    }

    cvtColor(result, result, COLOR_BGR2RGBA);
    return code;
}

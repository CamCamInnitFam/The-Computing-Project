#include <opencv2/opencv.hpp>
#include <iostream>
#include <vector>

double computeSSIM(const cv::Mat& img1, const cv::Mat& img2) {
    // Ensure images are 8-bit unsigned (CV_8U)
    if (img1.type() != CV_8U || img2.type() != CV_8U) {
        std::cout << "Error: Images must be CV_8U (8-bit unsigned)." << std::endl;
        return -1.0;
    }

    // Constants for stability (adjusted for 8-bit images)
    const double C1 = 6.5025; // (0.01 * 255)^2
    const double C2 = 58.5225; // (0.03 * 255)^2

    // Convert to float for precise calculations
    cv::Mat img1_float, img2_float;
    img1.convertTo(img1_float, CV_32F);
    img2.convertTo(img2_float, CV_32F);

    // Compute squares and product
    cv::Mat img1_sq, img2_sq, img1_img2;
    cv::pow(img1_float, 2, img1_sq);
    cv::pow(img2_float, 2, img2_sq);
    img1_img2 = img1_float.mul(img2_float);

    // Compute means with Gaussian blur
    cv::Mat mu1, mu2, mu1_sq, mu2_sq, mu1_mu2;
    cv::GaussianBlur(img1_float, mu1, cv::Size(11, 11), 1.5);
    cv::GaussianBlur(img2_float, mu2, cv::Size(11, 11), 1.5);
    cv::pow(mu1, 2, mu1_sq);
    cv::pow(mu2, 2, mu2_sq);
    mu1_mu2 = mu1.mul(mu2);

    // Compute variances and covariance
    cv::Mat sigma1_sq, sigma2_sq, sigma12;
    cv::GaussianBlur(img1_sq, sigma1_sq, cv::Size(11, 11), 1.5);
    sigma1_sq -= mu1_sq;
    cv::GaussianBlur(img2_sq, sigma2_sq, cv::Size(11, 11), 1.5);
    sigma2_sq -= mu2_sq;
    cv::GaussianBlur(img1_img2, sigma12, cv::Size(11, 11), 1.5);
    sigma12 -= mu1_mu2;

    // Compute SSIM map
    cv::Mat ssim_map;
    cv::Mat numerator = 2 * mu1_mu2 + C1;
    cv::Mat denominator = mu1_sq + mu2_sq + C1;
    cv::multiply(numerator, 2 * sigma12 + C2, numerator);
    cv::multiply(denominator, sigma1_sq + sigma2_sq + C2, denominator);
    cv::divide(numerator, denominator, ssim_map);

    // Return average SSIM
    cv::Scalar ssim = cv::mean(ssim_map);
    return ssim[0];
}

int main() {
    // Hardcoded image paths (modify to your image locations)
    std::string original_path = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688.jpg";
    std::string decompressed_path = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688_decompressed.jpg";

    // Load images in color
    cv::Mat original = cv::imread(original_path, cv::IMREAD_COLOR);
    cv::Mat decompressed = cv::imread(decompressed_path, cv::IMREAD_COLOR);

    // Check if images loaded successfully
    if (original.empty() || decompressed.empty()) {
        std::cout << "Error: One or both images failed to load.\n"
            << "Original path: " << original_path << "\n"
            << "Decompressed path: " << decompressed_path << std::endl;
        std::cout << "Press Enter to exit..." << std::endl;
        std::cin.get();
        return -1;
    }

    // Verify image properties
    std::cout << "Original size: " << original.cols << "x" << original.rows
        << ", Channels: " << original.channels() << std::endl;
    std::cout << "Decompressed size: " << decompressed.cols << "x" << decompressed.rows
        << ", Channels: " << decompressed.channels() << std::endl;

    // Check if images have the same dimensions and channels
    if (original.size() != decompressed.size() || original.channels() != decompressed.channels()) {
        std::cout << "Error: Images must have identical dimensions and channels." << std::endl;
        std::cout << "Press Enter to exit..." << std::endl;
        std::cin.get();
        return -1;
    }

    // Split channels
    std::vector<cv::Mat> original_channels, decompressed_channels;
    cv::split(original, original_channels);
    cv::split(decompressed, decompressed_channels);

    if (original_channels.size() != 3 || decompressed_channels.size() != 3) {
        std::cout << "Error: Images must have 3 channels (BGR)." << std::endl;
        std::cout << "Press Enter to exit..." << std::endl;
        std::cin.get();
        return -1;
    }

    // Compute SSIM for each channel
    double ssim_b = computeSSIM(original_channels[0], decompressed_channels[0]);
    double ssim_g = computeSSIM(original_channels[1], decompressed_channels[1]);
    double ssim_r = computeSSIM(original_channels[2], decompressed_channels[2]);

    // Check for invalid SSIM values
    if (ssim_b < 0 || ssim_g < 0 || ssim_r < 0) {
        std::cout << "Error: SSIM computation failed for one or more channels." << std::endl;
        std::cout << "Press Enter to exit..." << std::endl;
        std::cin.get();
        return -1;
    }

    // Average SSIM and print per-channel results
    double ssim_avg = (ssim_b + ssim_g + ssim_r) / 3.0;
    std::cout << "SSIM (Blue): " << ssim_b << std::endl;
    std::cout << "SSIM (Green): " << ssim_g << std::endl;
    std::cout << "SSIM (Red): " << ssim_r << std::endl;
    std::cout << "Average SSIM: " << ssim_avg << std::endl;

    // Pause to view output
    std::cout << "Press Enter to exit..." << std::endl;
    std::cin.get();

    return 0;
}
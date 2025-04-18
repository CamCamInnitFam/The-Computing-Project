#include <opencv2/opencv.hpp>
#include <iostream>
#include <vector>
#include <string>

int main() {
    std::string input_path = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688.jpg";

    cv::Mat image = cv::imread(input_path, cv::IMREAD_COLOR);
    if (image.empty()) {
        std::cout << "Error: Could not load image at " << input_path << std::endl;
        std::cout << "Press Enter to exit..." << std::endl;
        std::cin.get();
        return -1;
    }

    std::cout << "Loaded image: " << input_path << "\n"
        << "Size: " << image.cols << "x" << image.rows << ", Channels: " << image.channels() << std::endl;

    std::vector<int> quality_levels = { 100, 75, 50 };

    //save at each quality level
    for (int quality : quality_levels) {
        std::string output_path = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\test_" + std::to_string(quality) + ".jpg";

        std::vector<int> compression_params;
        compression_params.push_back(cv::IMWRITE_JPEG_QUALITY);
        compression_params.push_back(quality);

        //save
        bool success = cv::imwrite(output_path, image, compression_params);
        if (!success) {
            std::cout << "Error: Failed to save image at " << output_path << std::endl;
            continue;
        }

        //verify
        cv::Mat saved_image = cv::imread(output_path, cv::IMREAD_COLOR);
        if (saved_image.empty()) {
            std::cout << "Error: Could not verify saved image at " << output_path << std::endl;
            continue;
        }

        //calc file size
        FILE* file = nullptr;
        errno_t err = fopen_s(&file, output_path.c_str(), "rb");
        long file_size = 0;
        if (err == 0 && file) {
            fseek(file, 0, SEEK_END);
            file_size = ftell(file);
            fclose(file);
        }
        else {
            std::cout << "Warning: Could not compute file size for " << output_path << std::endl;
        }

        std::cout << "Saved: " << output_path << "\n"
            << "Quality: " << quality << ", File Size: " << file_size / 1024.0 << " KB" << std::endl;
    }

    //view output in IDE
    std::cout << "Press Enter to exit..." << std::endl;
    std::cin.get();

    return 0;
}
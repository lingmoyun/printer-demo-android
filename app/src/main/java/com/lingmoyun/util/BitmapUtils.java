package com.lingmoyun.util;

import android.graphics.Bitmap;
import android.graphics.Color;

public class BitmapUtils {

    public static Bitmap floydSteinberg(Bitmap src) {

        //Getting configuration to get the width and height of the bitmap
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());

        int alpha, red;
        int pixel;
        int gray;
        int threshold = 128;

        int width = src.getWidth();
        int height = src.getHeight();
        int error = 0;
        int[][] errors = new int[width][height];
        for (int y = 0; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                pixel = src.getPixel(x, y);

                alpha = Color.alpha(pixel);
                red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);

                //using red,green,blue with constant values to get gray color

                int grayC = (int) (0.21 * red + 0.72 * green + 0.07 * blue);
                gray = grayC;
                if (gray + errors[x][y] < threshold) {
                    error = gray + errors[x][y];
                    gray = 0;
                } else {
                    error = gray + errors[x][y] - 255;
                    gray = 255;
                }
                errors[x + 1][y] += (7 * error) / 16;
                errors[x - 1][y + 1] += (3 * error) / 16;
                errors[x][y + 1] += (5 * error) / 16;
                errors[x + 1][y + 1] += (1 * error) / 16;

                out.setPixel(x, y, Color.argb(alpha, gray, gray, gray));

            }
        }

        return out;
    }
}

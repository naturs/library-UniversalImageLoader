/*******************************************************************************
 * Copyright 2013-2014 Sergey Tarasevich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.nostra13.universalimageloader.utils;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.BitmapFactory;
import android.opengl.GLES10;

import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.assist.ViewScaleType;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;

/**
 * Provides calculations with image sizes, scales
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.8.3
 */
public final class ImageSizeUtils {

	private static final int DEFAULT_MAX_BITMAP_DIMENSION = 2048;

	private static ImageSize maxBitmapSize;

	static {
		int[] maxTextureSize = new int[1];
		GLES10.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);
		int maxBitmapDimension = Math.max(maxTextureSize[0], DEFAULT_MAX_BITMAP_DIMENSION);
		maxBitmapSize = new ImageSize(maxBitmapDimension, maxBitmapDimension);
	}

	private ImageSizeUtils() {
	}

	/**
	 * Defines target size for image aware view. Size is defined by target
	 * {@link com.nostra13.universalimageloader.core.imageaware.ImageAware view} parameters, configuration
	 * parameters or device display dimensions.<br />
	 */
	public static ImageSize defineTargetSizeForView(ImageAware imageAware, ImageSize maxImageSize) {
		int width = imageAware.getWidth();
		if (width <= 0) width = maxImageSize.getWidth();

		int height = imageAware.getHeight();
		if (height <= 0) height = maxImageSize.getHeight();

		return new ImageSize(width, height);
	}

	/**
	 * Computes sample size for downscaling image size (<b>srcSize</b>) to view size (<b>targetSize</b>). This sample
	 * size is used during
	 * {@linkplain BitmapFactory#decodeStream(java.io.InputStream, android.graphics.Rect, android.graphics.BitmapFactory.Options)
	 * decoding image} to bitmap.<br />
	 * <br />
	 * <b>Examples:</b><br />
	 * <p/>
	 * <pre>
	 * srcSize(100x100), targetSize(10x10), powerOf2Scale = true -> sampleSize = 8
	 * srcSize(100x100), targetSize(10x10), powerOf2Scale = false -> sampleSize = 10
	 *
	 * srcSize(100x100), targetSize(20x40), viewScaleType = FIT_INSIDE -> sampleSize = 5
	 * srcSize(100x100), targetSize(20x40), viewScaleType = CROP       -> sampleSize = 2
	 * </pre>
	 * <p/>
	 * <br />
	 * The sample size is the number of pixels in either dimension that correspond to a single pixel in the decoded
	 * bitmap. For example, inSampleSize == 4 returns an image that is 1/4 the width/height of the original, and 1/16
	 * the number of pixels. Any value <= 1 is treated the same as 1.
	 *
	 * @param srcSize       Original (image) size
	 * @param targetSize    Target (view) size
	 * @param viewScaleType {@linkplain ViewScaleType Scale type} for placing image in view
	 * @param powerOf2Scale <i>true</i> - if sample size be a power of 2 (1, 2, 4, 8, ...)
	 * @return Computed sample size
	 */
	public static int computeImageSampleSize(ImageSize srcSize, ImageSize targetSize, ViewScaleType viewScaleType,
			boolean powerOf2Scale) {
		final int srcWidth = srcSize.getWidth();
		final int srcHeight = srcSize.getHeight();
		final int targetWidth = targetSize.getWidth();
		final int targetHeight = targetSize.getHeight();

		int scale = 1;

		L.d("powerOf2Scale:%s", powerOf2Scale);
		
		switch (viewScaleType) {
			case FIT_INSIDE:
				if (powerOf2Scale) {
					final int halfWidth = srcWidth / 2;
					final int halfHeight = srcHeight / 2;
					while ((halfWidth / scale) > targetWidth || (halfHeight / scale) > targetHeight) { // ||
						scale *= 2;
					}
				} else {
					// 要保证Image的两个边都在View内
					scale = Math.max(srcWidth / targetWidth, srcHeight / targetHeight); // max
				}
				break;
			case CROP:
				if (powerOf2Scale) {
					final int halfWidth = srcWidth / 2;
					final int halfHeight = srcHeight / 2;
					while ((halfWidth / scale) > targetWidth && (halfHeight / scale) > targetHeight) { // &&
						scale *= 2;
					}
				} else {
					// 只需要Image的一个边在View内就可以了
					scale = Math.min(srcWidth / targetWidth, srcHeight / targetHeight); // min
				}
				break;
		}

		if (scale < 1) {
			scale = 1;
		}
		scale = considerMaxTextureSize(srcWidth, srcHeight, scale, powerOf2Scale);

		return scale;
	}

	private static int considerMaxTextureSize(int srcWidth, int srcHeight, int scale, boolean powerOf2) {
		final int maxWidth = maxBitmapSize.getWidth();
		final int maxHeight = maxBitmapSize.getHeight();
		while ((srcWidth / scale) > maxWidth || (srcHeight / scale) > maxHeight) {
			if (powerOf2) {
				scale *= 2;
			} else {
				scale++;
			}
		}
		return scale;
	}

	/**
	 * Computes minimal sample size for downscaling image so result image size won't exceed max acceptable OpenGL
	 * texture size.<br />
	 * We can't create Bitmap in memory with size exceed max texture size (usually this is 2048x2048) so this method
	 * calculate minimal sample size which should be applied to image to fit into these limits.
	 *
	 * @param srcSize Original image size
	 * @return Minimal sample size
	 */
	public static int computeMinImageSampleSize(ImageSize srcSize) {
		final int srcWidth = srcSize.getWidth();
		final int srcHeight = srcSize.getHeight();
		final int targetWidth = maxBitmapSize.getWidth();
		final int targetHeight = maxBitmapSize.getHeight();

		final int widthScale = (int) Math.ceil((float) srcWidth / targetWidth);
		final int heightScale = (int) Math.ceil((float) srcHeight / targetHeight);

		return Math.max(widthScale, heightScale); // max
	}

	/**
	 * <p>当取值为ImageScaleType.EXACTLY或ImageScaleType.EXACTLY_STRETCHED的时候，才执行该方法，目的是计算出
	 * 一个精确的缩放比例，使最终的Bitmap大小刚好和ImageView大小一致.</p>
	 * Computes scale of target size (<b>targetSize</b>) to source size (<b>srcSize</b>).<br />
	 * <br />
	 * <b>Examples:</b><br />
	 * <p/>
	 * <pre>
	 * srcSize(40x40), targetSize(10x10) -> scale = 0.25
	 *
	 * srcSize(10x10), targetSize(20x20), stretch = false -> scale = 1
	 * srcSize(10x10), targetSize(20x20), stretch = true  -> scale = 2
	 *
	 * srcSize(100x100), targetSize(20x40), viewScaleType = FIT_INSIDE -> scale = 0.2
	 * srcSize(100x100), targetSize(20x40), viewScaleType = CROP       -> scale = 0.4
	 * </pre>
	 *
	 * @param srcSize       Source (image) size
	 * @param targetSize    Target (view) size
	 * @param viewScaleType {@linkplain ViewScaleType Scale type} for placing image in view
	 * @param stretch       Whether source size should be stretched if target size is larger than source size. If <b>false</b>
	 *                      then result scale value can't be greater than 1.
	 * @return Computed scale
	 */
	public static float computeImageScale(ImageSize srcSize, ImageSize targetSize, ViewScaleType viewScaleType,
			boolean stretch) {
		final int srcWidth = srcSize.getWidth();
		final int srcHeight = srcSize.getHeight();
		final int targetWidth = targetSize.getWidth();
		final int targetHeight = targetSize.getHeight();

		final float widthScale = (float) srcWidth / targetWidth;
		final float heightScale = (float) srcHeight / targetHeight;
		
		final int destWidth;
		final int destHeight;
		/**
		 * 按缩放类型，得到最终Image的宽度和高度.
		 * 
		 * FIT_INSIDE: 按缩放比例大的方向来缩放
		 * CROP      : 按缩放比例小的方向来缩放
		 * 
		 * 如果Image比View小，就按各自的方式放大Image.但这里放大还没有影响结果，还需要看stretch参数.
		 * 
		 * 如果把下面的if else改成这种switch，会更容易理解.
		 * switch (viewScaleType) {
		 * case ViewScaleType.FIT_INSIDE:
		 * 		if (widthScale >= heightScale) {
		 * 			destWidth = targetWidth;
		 * 			destHeight = (int) (srcHeight / widthScale);
		 * 		} else {
		 * 			destWidth = (int) (srcWidth / heightScale);
		 * 			destHeight = targetHeight;
		 * 		}
		 * 		break;
		 * 
		 * case ViewScaleType.CROP:
		 * 		if (widthScale < heightScale) {
		 * 			destWidth = targetWidth;
		 * 			destHeight = (int) (srcHeight / widthScale);
		 * 		} else {
		 * 			destWidth = (int) (srcWidth / heightScale);
		 * 			destHeight = targetHeight;
		 * 		}
		 * 		break;
		 * }
		 */
		if ((viewScaleType == ViewScaleType.FIT_INSIDE && widthScale >= heightScale) || (viewScaleType == ViewScaleType.CROP && widthScale < heightScale)) {
//			destWidth = targetWidth;
			destWidth = (int) (srcWidth / widthScale); // srcWidth / (srcWidth / targetWidth)
			destHeight = (int) (srcHeight / widthScale);
		} else {
			destWidth = (int) (srcWidth / heightScale);
			destHeight = targetHeight;
		}
		
		// 上面计算出来的destWidth和destHeight值有可能比原图大，可能是不符合要求的

		L.d("computeImageScale => src W=%1$d,H=%2$d target W=%3$d,H=%4$d scale w=%5$f,h=%6$f dest w:%7$d,h=%8$d", srcWidth, srcHeight, targetWidth, targetHeight, widthScale, heightScale, destWidth, destHeight);
		
		/**
		 * (srcWidth x srcHeight) -> (destWidth x destHeight)
		 * 上面的转换是经过了同一个scale的乘法操作
		 * 
		 * 计算缩放比例
		 * 1. 如果不需要拉伸图片，图片变小了，计算新的scale值
		 * 2. 如果需要拉伸图片，新图和原图不一样，计算新的scale值
		 *    |- 目标图比原图小，会缩小原图，和上面一样
		 *    |- 目标图比原图大，会放大原图，这就是ImageScaleType.EXACTLY和ImageScaleType.EXACTLY_STRETCHED不一样的地方
		 */
		float scale = 1;
		if ((!stretch && destWidth < srcWidth && destHeight < srcHeight) || (stretch && destWidth != srcWidth && destHeight != srcHeight)) {
			scale = (float) destWidth / srcWidth;
			// 值和上面的是一样的
//			scale = (float) destHeight / srcHeight;
		}

		// TODO just for test
		computeImageScale2(srcSize, targetSize, viewScaleType, stretch);
		
		return scale;
	}
	
	public static float computeImageScale2(ImageSize srcSize, ImageSize targetSize, ViewScaleType viewScaleType,
			boolean stretch) {
		final int srcWidth = srcSize.getWidth();
		final int srcHeight = srcSize.getHeight();
		final int targetWidth = targetSize.getWidth();
		final int targetHeight = targetSize.getHeight();

		final float widthScale = (float) targetWidth / srcWidth;
		final float heightScale = (float) targetHeight / srcHeight;
		
		float scale = 1.0f;
		
		// 这个scale值有可能大于1，代表图片会被放大
		if (viewScaleType == ViewScaleType.FIT_INSIDE) {
			scale = Math.min(widthScale, heightScale);
		} else if (viewScaleType == ViewScaleType.CROP) {
			scale = Math.max(widthScale, heightScale);
		}
		
		if (!stretch) {
			scale = Math.min(1.0f, scale);
		}
		
		L.d("my scale is %f", scale);
		
		return scale;
	}
}

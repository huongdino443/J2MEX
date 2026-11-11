/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package javax.microedition.m3g;

import java.awt.image.BufferedImage;

public class Image2D extends Object3D
{

	public static final int ALPHA = 96;
	public static final int LUMINANCE = 97;
	public static final int LUMINANCE_ALPHA = 98;
	public static final int RGB = 99;
	public static final int RGBA = 100;


	private byte[] image;
	private int[] pixels;
	private int width = 0;
	private int height = 0;
	private int format = RGB;
	private boolean mutable;

	public Image2D(int fmt, int w, int h)
	{
		checkFormat(fmt);
		if (w < 1 || h < 1) throw new IllegalArgumentException("Invalid image size");
		width=w; height=h; format=fmt; mutable=true; pixels=new int[w*h];
	}

	public Image2D(int fmt, int w, int h, byte[] img)
	{
		this(fmt, w, h);
		if (img == null) throw new NullPointerException("image");
		image=img.clone(); mutable=false; pixels=decode(fmt,w,h,img,null);
	}

	public Image2D(int fmt, int w, int h, byte[] img, byte[] palette)
	{
		this(fmt, w, h);
		if (img == null || palette == null) throw new NullPointerException();
		image=img.clone(); mutable=false; pixels=decode(fmt,w,h,img,palette);
	}

	public Image2D(int fmt, Object img)
	{
		checkFormat(fmt);
		if (img == null) throw new NullPointerException("image");
		format=fmt; mutable=false;
		if (img instanceof javax.microedition.lcdui.Image) {
			javax.microedition.lcdui.Image source=(javax.microedition.lcdui.Image)img;
			width=source.getWidth(); height=source.getHeight(); pixels=new int[width*height];
			source.getRGB(pixels,0,width,0,0,width,height);
		} else if (img instanceof BufferedImage) {
			BufferedImage source=(BufferedImage)img;
			width=source.getWidth(); height=source.getHeight(); pixels=source.getRGB(0,0,width,height,null,0,width);
		} else if (img instanceof int[]) {
			throw new IllegalArgumentException("Image dimensions required for pixel array");
		} else {
			throw new IllegalArgumentException("Unsupported Image2D source: " + img.getClass().getName());
		}
	}


	public int getFormat() { return format; }

	public int getHeight() { return height; }

	public int getWidth() { return width; }

	public boolean isMutable() { return mutable; }

	public void set(int x, int y, int w, int h, byte[] img)
	{
		if (!mutable) throw new IllegalStateException("Image2D is immutable");
		if (img == null || x < 0 || y < 0 || w < 1 || h < 1 || x+w > width || y+h > height)
			throw new IllegalArgumentException("Invalid image update");
		int bytesPerPixel = format == ALPHA || format == LUMINANCE ? 1 : format == LUMINANCE_ALPHA ? 2 : format == RGB ? 3 : 4;
		if (img.length < w*h*bytesPerPixel) throw new IllegalArgumentException("Pixel data is too small");
		int[] decoded=decode(format,w,h,img,null);
		for (int row=0; row<h; row++)
			System.arraycopy(decoded,row*w,pixels,(y+row)*width+x,w);
	}

	int[] pixels() { return pixels; }

	private static void checkFormat(int fmt)
	{
		if (fmt < ALPHA || fmt > RGBA) throw new IllegalArgumentException("Unsupported Image2D format");
	}

	private static int[] decode(int fmt,int width,int height,byte[] data,byte[] palette)
	{
		int count=width*height;
		int[] result=new int[count];
		if (palette != null) {
			int paletteStride=(fmt==RGBA || fmt==LUMINANCE_ALPHA) ? 4 : 3;
			if (palette.length < paletteStride) throw new IllegalArgumentException("Palette is empty");
			int entries=palette.length/paletteStride;
			if (data.length < count) throw new IllegalArgumentException("Index data is too small");
			for (int i=0;i<count;i++) {
				int index=data[i]&255;
				if (index>=entries) throw new IllegalArgumentException("Palette index outside palette");
				int p=index*paletteStride;
				int r=palette[p]&255,g=palette[p+1]&255,b=palette[p+2]&255;
				int a=paletteStride==4?palette[p+3]&255:255;
				result[i]=(a<<24)|(r<<16)|(g<<8)|b;
			}
			return result;
		}
		int stride=fmt==ALPHA || fmt==LUMINANCE ? 1 : fmt==LUMINANCE_ALPHA ? 2 : fmt==RGB ? 3 : 4;
		if (data.length < count*stride) throw new IllegalArgumentException("Image data is too small");
		for (int i=0;i<count;i++) {
			int p=i*stride,r,g,b,a=255;
			if (fmt==ALPHA) { a=data[p]&255; r=g=b=255; }
			else if (fmt==LUMINANCE) { r=g=b=data[p]&255; }
			else if (fmt==LUMINANCE_ALPHA) { r=g=b=data[p]&255; a=data[p+1]&255; }
			else { r=data[p]&255; g=data[p+1]&255; b=data[p+2]&255; if (fmt==RGBA) a=data[p+3]&255; }
			result[i]=(a<<24)|(r<<16)|(g<<8)|b;
		}
		return result;
	}
}

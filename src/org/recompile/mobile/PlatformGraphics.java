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
package org.recompile.mobile;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;

import com.nokia.mid.ui.DirectGraphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

public class PlatformGraphics extends javax.microedition.lcdui.Graphics implements DirectGraphics
{
	protected BufferedImage canvas;
	protected Graphics2D gc;

	protected Color awtColor;

	protected int strokeStyle = SOLID;


	public PlatformGraphics platformGraphics;
	public PlatformImage platformImage;

	public PlatformGraphics(PlatformImage image)
	{
		canvas = image.getCanvas();
		gc = canvas.createGraphics();
		platformImage = image;

		platformGraphics = this;

		clipX = 0;
		clipY = 0;
		clipWidth = canvas.getWidth();
		clipHeight = canvas.getHeight();

		setColor(0,0,0);
		gc.setBackground(new Color(0, 0, 0, 0));
		gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		gc.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
				RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		gc.setFont(font.platformFont.awtFont);
	}

	public void reset() //Internal use method, resets the Graphics object to its inital values
	{
		translate(-1 * translateX, -1 * translateY);
		setClip(0, 0, canvas.getWidth(), canvas.getHeight());
		setColor(0,0,0);
		setFont(Font.getDefaultFont());
		setStrokeStyle(SOLID);
	}

	public Graphics2D getGraphics2D()
	{
		return gc;
	}

	public BufferedImage getCanvas()
	{
		return canvas;
	}

	public void clearRect(int x, int y, int width, int height)
	{
		gc.clearRect(x, y, width, height);
	}

	public void copyArea(int subx, int suby, int subw, int subh, int x, int y, int anchor)
	{
if (subw <= 0 || subh <= 0) return;

// Snapshot before writing: a camera scroll often copies overlapping areas of
// this same canvas. A getSubimage() view aliases the destination, so drawing it
// back can smear one source pixel across the whole region.
int[] pixels = new int[subw * subh];
canvas.getRGB(subx, suby, subw, subh, pixels, 0, subw);

int destX = AnchorX(x, subw, anchor) + translateX;
int destY = AnchorY(y, subh, anchor) + translateY;
int left = Math.max(Math.max(destX, clipX + translateX), 0);
int top = Math.max(Math.max(destY, clipY + translateY), 0);
int right = Math.min(Math.min(destX + subw, clipX + translateX + clipWidth), canvas.getWidth());
int bottom = Math.min(Math.min(destY + subh, clipY + translateY + clipHeight), canvas.getHeight());
if (right <= left || bottom <= top) return;

if (canvas.getType() == BufferedImage.TYPE_INT_ARGB
		|| canvas.getType() == BufferedImage.TYPE_INT_RGB)
{
	DataBufferInt buffer = (DataBufferInt)canvas.getRaster().getDataBuffer();
	SinglePixelPackedSampleModel sample =
			(SinglePixelPackedSampleModel)canvas.getRaster().getSampleModel();
	int[] destination = buffer.getData();
	int destinationStart = buffer.getOffset() + sample.getOffset(left, top);
	int stride = sample.getScanlineStride();
	int sourceStart = (top - destY) * subw + (left - destX);
	for (int row = 0; row < bottom - top; row++)
	{
		int source = sourceStart + row * subw;
		int dest = destinationStart + row * stride;
		for (int col = 0; col < right - left; col++)
		{
			// J2ME Loader copies this region as an opaque bitmap, not as
			// SrcOver artwork; preserve that behavior for framebuffer scrolls.
			destination[dest + col] = pixels[source + col] | 0xff000000;
		}
	}
}
else
{
	for (int row = top; row < bottom; row++)
	{
		for (int col = left; col < right; col++)
		{
			int source = (row - destY) * subw + (col - destX);
			canvas.setRGB(col, row, pixels[source] | 0xff000000);
		}
	}
}
	}

	public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle)
	{
		gc.drawArc(x, y, width, height, startAngle, arcAngle);
	}

	public void drawChar(char character, int x, int y, int anchor)
	{
		drawString(Character.toString(character), x, y, anchor);
	}

	public void drawChars(char[] data, int offset, int length, int x, int y, int anchor)
	{
		char[] str = new char[length];
		for(int i=offset; i<offset+length; i++)
		{
			if(i>=0 && i<data.length)
			{
				str[i-offset] = data[i];
			}
		}	
		drawString(new String(str), x, y, anchor);
	}

	public void drawImage(Image image, int x, int y, int anchor)
	{
		try
		{
			int imgWidth = image.getWidth();
			int imgHeight = image.getHeight();

			x = AnchorX(x, imgWidth, anchor);
			y = AnchorY(y, imgHeight, anchor);

			gc.drawImage(image.platformImage.getCanvas(), x, y, null);
		}
		catch (Exception e)
		{
			System.out.println("drawImage A:"+e.getMessage());
		}
	}

	public void drawImage(Image image, int x, int y)
	{
		try
		{
			gc.drawImage(image.platformImage.getCanvas(), x, y, null);
		}
		catch (Exception e)
		{
			System.out.println("drawImage B:"+e.getMessage());
		}
	}

	public void drawImage2(Image image, int x, int y) // Internal use method called by PlatformImage
	{
		gc.drawImage(image.platformImage.getCanvas(), x, y, null);
	}
	public void drawImage2(BufferedImage image, int x, int y) // Internal use method called by PlatformImage
	{
		gc.drawImage(image, x, y, null);
	}

	public void drawImage2Test(BufferedImage image, int x, int y)
	{
		// This Fixes some transparency issues with some images drawn 
		// with Nokia drawImage in a few games.  
		// There's probably a deeper underlying issue.
		int row=0;
		int col=0; 
		int imgWidth = image.getWidth();
		int imgHeight = image.getHeight();
		int cwidth = canvas.getWidth();
		int cheight = canvas.getHeight();
		int width = imgWidth;
		int height = imgHeight;

		try
		{
			int[] datargb = image.getRGB(0,0, imgWidth,imgHeight, null,0,imgWidth);
			if(x+width>=cwidth+1) { width -= ((x+width)-cwidth); }
			if(y+height>=cheight+1) { height-=((y+height)-cheight); }
			for(row=0; row<height; row++)
			{
				for(col=0; col<width; col++)
				{
					int c = datargb[col+ row*imgWidth] & 0xFFFFFFFF;
					if(c!=0xFF000000)
					{
						canvas.setRGB(col+x, row+y, c);
					}
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("drawImage C:"+e.getMessage());
		}
	}

	public void flushGraphics(Image image, int x, int y, int width, int height)
	{
		// called by MobilePlatform.flushGraphics/repaint
		try
		{
			BufferedImage sub = image.platformImage.getCanvas().getSubimage(x, y, width, height);
			gc.drawImage(sub, x, y, null);
		}
		catch (Exception e)
		{
			//System.out.println("flushGraphics A:"+e.getMessage());
		}
	}

	public void drawRegion(Image image, int subx, int suby, int subw, int subh, int transform, int x, int y, int anchor)
	{
		try
		{
			if(transform == 0)
			{
				x = AnchorX(x, subw, anchor);
				y = AnchorY(y, subh, anchor);
				BufferedImage sub = image.platformImage.getCanvas().getSubimage(subx, suby, subw, subh);
				gc.drawImage(sub, x, y, null);
			}
			else
			{
				PlatformImage sub = new PlatformImage(image, subx, suby, subw, subh, transform);
				x = AnchorX(x, sub.width, anchor);
				y = AnchorY(y, sub.height, anchor);
				gc.drawImage(sub.getCanvas(), x, y, null);
			}
		}
		catch (Exception e)
		{
			//System.out.println("drawRegion A (x:"+x+" y:"+y+" w:"+subw+" h:"+subh+"):"+e.getMessage());
		}
	}

	public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha)
	{
		if (width < 1 || height < 1) return;
		if (rgbData == null) throw new NullPointerException("rgbData");
		long first=offset, last=first+(long)(height-1)*scanlength;
		if(Math.min(first,last)<0 || Math.max(first,last)+width>rgbData.length)
			throw new ArrayIndexOutOfBoundsException("RGB source outside array");
		if(canvas.getType()==BufferedImage.TYPE_INT_ARGB||canvas.getType()==BufferedImage.TYPE_INT_RGB) {
			int dx=x+translateX, dy=y+translateY;
			int left=Math.max(Math.max(dx,clipX+translateX),0);
			int top=Math.max(Math.max(dy,clipY+translateY),0);
			int right=Math.min(Math.min(dx+width,clipX+translateX+clipWidth),canvas.getWidth());
			int bottom=Math.min(Math.min(dy+height,clipY+translateY+clipHeight),canvas.getHeight());
			if(right<=left||bottom<=top) return;
			DataBufferInt buffer=(DataBufferInt)canvas.getRaster().getDataBuffer();
			SinglePixelPackedSampleModel sample=(SinglePixelPackedSampleModel)canvas.getRaster().getSampleModel();
			int[] destination=buffer.getData();
			int destinationStart=buffer.getOffset()+sample.getOffset(left,top);
			int sourceStart=offset+(top-dy)*scanlength+(left-dx);
			int stride=sample.getScanlineStride();
			for(int row=0;row<bottom-top;row++) {
				int source=sourceStart+row*scanlength,dest=destinationStart+row*stride;
				for(int col=0;col<right-left;col++) {
					int pixel=rgbData[source+col];
					destination[dest+col]=processAlpha?over(pixel,destination[dest+col]):pixel|0xff000000;
				}
			}
			return;
		}
		BufferedImage temp=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
		if(processAlpha) temp.setRGB(0,0,width,height,rgbData,offset,scanlength);
		else {
			int[] opaque=new int[width*height];
			for(int row=0;row<height;row++) for(int col=0;col<width;col++)
				opaque[row*width+col]=rgbData[offset+row*scanlength+col]|0xff000000;
			temp.setRGB(0,0,width,height,opaque,0,width);
		}
		gc.drawImage(temp,x,y,null);
	}

	private static int over(int src,int dst)
	{
		int sa=src>>>24;
		if(sa==255) return src;
		if(sa==0) return dst;
		int da=dst>>>24, inv=255-sa, oa=sa+(da*inv+127)/255;
		return (oa<<24)|(overChannel((src>>>16)&255,(dst>>>16)&255,sa,da,inv,oa)<<16)
				|(overChannel((src>>>8)&255,(dst>>>8)&255,sa,da,inv,oa)<<8)
				|overChannel(src&255,dst&255,sa,da,inv,oa);
	}

	private static int overChannel(int s,int d,int sa,int da,int inv,int oa)
	{
		return oa==0?0:(s*sa*255+d*da*inv+oa*127)/(oa*255);
	}


	public void drawLine(int x1, int y1, int x2, int y2)
	{
		gc.drawLine(x1, y1, x2, y2);
	}

	public void drawRect(int x, int y, int width, int height)
	{
		gc.drawRect(x, y, width, height);
	}

	public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
	{
		gc.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
	}

	public void drawString(String str, int x, int y, int anchor)
	{
if(str!=null && str.length()>0)
		{
if(font.platformFont.hasAndroidFontBridge())
{
drawStringWithAndroidCanvas(str, x, y, anchor);
return;
}
			gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			if (font.platformFont.isMonospaceFace()) {
				gc.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
						RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
				java.awt.FontMetrics metrics = gc.getFontMetrics(font.platformFont.awtFont);
				int drawX = AnchorX(x, metrics.stringWidth(str), anchor);
				int baseline = stringBaseline(y, metrics.getAscent(),
						metrics.getHeight(), anchor);
				font.platformFont.drawString(gc, str, drawX, baseline);
				return;
			}
			gc.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
					RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			java.awt.FontMetrics metrics = gc.getFontMetrics(font.platformFont.awtFont);
			TextLayout layout = font.platformFont.textLayout(gc, str);
			float drawX = AnchorX((float)x, layout.getAdvance(), anchor);
			y = androidStringBaseline(y, metrics.getAscent(), metrics.getHeight(),
					font.platformFont.getAscentOffset(), anchor);
			layout.draw(gc, drawX, y);
		}
	}

private void drawStringWithAndroidCanvas(String str, int x, int y, int anchor)
{
AndroidFontBridge.RenderedText text = font.platformFont.renderAndroidText(str);
if(text == null || text.width == 0 || text.height == 0) return;
int color = awtColor.getRGB();
int colorAlpha = (color >>> 24) & 0xff;
int colorRgb = color & 0x00ffffff;
int[] pixels = new int[text.mask.length];
for(int i = 0; i < pixels.length; i++)
{
int alpha = ((text.mask[i] >>> 24) & 0xff) * colorAlpha / 255;
pixels[i] = (alpha << 24) | colorRgb;
}
BufferedImage bitmap = new BufferedImage(text.width, text.height, BufferedImage.TYPE_INT_ARGB);
bitmap.setRGB(0, 0, text.width, text.height, pixels, 0, text.width);
float drawX = AnchorX((float)x, (int)text.advance, anchor);
int baseline = androidStringBaseline(y, text.metrics.ascent,
		text.metrics.height(), text.metrics.ascentOffset, anchor);
gc.drawImage(bitmap, AffineTransform.getTranslateInstance(
		(double)drawX + text.left, (double)baseline + text.top), null);
}

	public void drawSubstring(String str, int offset, int len, int x, int y, int anchor)
	{
		if (str.length() >= offset + len)
		{
			drawString(str.substring(offset, offset+len), x, y, anchor);
		}
	}

	public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle)
	{
		gc.fillArc(x, y, width, height, startAngle, arcAngle);
	}

	public void fillRect(int x, int y, int width, int height)
	{
		gc.fillRect(x, y, width, height);
	}

	public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
	{
		gc.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
		gc.fillRect(x, y, width, height);
	}

	//public int getBlueComponent() { }
	//public Font getFont() { return font; }
	//public int getColor() { return color; }
	//public int getGrayScale() { }
	//public int getGreenComponent() { }
	//public int getRedComponent() { }
	//public int getStrokeStyle() { return strokeStyle; }

	public void setColor(int rgb)
	{
		setColor((rgb>>16) & 0xFF, (rgb>>8) & 0xFF, rgb & 0xFF);
	}

	public void setColor(int r, int g, int b)
	{
		color = (r<<16) + (g<<8) + b;
		colorAlpha = 0xFF;
		awtColor = new Color(r, g, b);
		gc.setColor(awtColor);
	}

	public void setFont(Font font)
	{
		super.setFont(font);
		gc.setFont(font.platformFont.awtFont);
	}
	//public void setGrayScale(int value)
	//public void setStrokeStyle(int style)

	public void setClip(int x, int y, int width, int height)
	{
		gc.setClip(x, y, width, height);
		clipX = (int)gc.getClipBounds().getX();
		clipY = (int)gc.getClipBounds().getY();
		clipWidth = (int)gc.getClipBounds().getWidth();
		clipHeight = (int)gc.getClipBounds().getHeight();
	}

	public void clipRect(int x, int y, int width, int height)
	{
		gc.clipRect(x, y, width, height);
		clipX = (int)gc.getClipBounds().getX();
		clipY = (int)gc.getClipBounds().getY();
		clipWidth = (int)gc.getClipBounds().getWidth();
		clipHeight = (int)gc.getClipBounds().getHeight();
	}

	//public int getTranslateX() { }
	//public int getTranslateY() { }

	public void translate(int x, int y)
	{
		translateX += x;
		translateY += y;
		gc.translate(x, y);
		clipX -= x;
		clipY -= y;
	}

	private int AnchorX(int x, int width, int anchor)
	{
		int xout = x;
		if((anchor & HCENTER)>0) { xout = x-(width/2); }
		if((anchor & RIGHT)>0) { xout = x-width; }
		if((anchor & LEFT)>0) { xout = x; }
		return xout;
	}

	private float AnchorX(float x, float width, int anchor)
	{
		float xout = x;
		if((anchor & HCENTER)>0) { xout = x-(width/2.0f); }
		if((anchor & RIGHT)>0) { xout = x-width; }
		if((anchor & LEFT)>0) { xout = x; }
		return xout;
	}

static int stringBaseline(int y, int ascent, int height, int anchor)
{
if((anchor & BASELINE) != 0) return y;
return AnchorY(y + ascent - 1, height, anchor);
}

static int androidStringBaseline(int y, int ascent, int height, int ascentOffset, int anchor)
{
return stringBaseline(y, ascent + ascentOffset, height, anchor) - ascentOffset;
}

private static int AnchorY(int y, int height, int anchor)
	{
		int yout = y;
		if((anchor & VCENTER)>0) { yout = y-(height/2); }
		if((anchor & TOP)>0) { yout = y; }
		if((anchor & BOTTOM)>0) { yout = y-height; }
		if((anchor & BASELINE)>0) { yout = y+height; }
		return yout;
	}

	public void setAlphaRGB(int ARGB)
	{
		gc.setColor(new Color(ARGB, true));
	}

	/*
		****************************
			Nokia Direct Graphics
		****************************
	*/
	// http://www.j2megame.org/j2meapi/Nokia_UI_API_1_1/com/nokia/mid/ui/DirectGraphics.html

	private int colorAlpha;

	public int getNativePixelFormat() { return DirectGraphics.TYPE_INT_8888_ARGB; }

	public int getAlphaComponent() { return colorAlpha; }

	public void setARGBColor(int argbColor)
	{
		colorAlpha = (argbColor>>>24) & 0xFF;
		color = argbColor & 0x00FFFFFF;
		awtColor = new Color(argbColor, true);
		gc.setColor(awtColor);
	}

	public void drawImage(javax.microedition.lcdui.Image img, int x, int y, int anchor, int manipulation)
	{
		//System.out.println("Nokia drawImage");
		BufferedImage image = manipulateImage(img.platformImage.getCanvas(), manipulation);
		x = AnchorX(x, image.getWidth(), anchor);
		y = AnchorY(y, image.getHeight(), anchor);
		drawImage2(image, x, y);
		//drawImage2Test(image, x, y);
	}

	public void drawPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format)
	{
		//System.out.println("drawPixels A "+format); // Found In Use
		int[] Type1 = {0xFFFFFFFF, 0xFF000000, 0x00FFFFFF, 0x00000000};
		int c = 0;
		int[] data;
		BufferedImage temp;
		switch(format)
		{
			case -1: // TYPE_BYTE_1_GRAY_VERTICAL // used by Monkiki's Castles
				data = new int[width*height];
				int ods = offset / scanlength;
				int oms = offset % scanlength;
				int b = ods % 8; //Bit offset in a byte
				for (int yj = 0; yj < height; yj++)
				{
					int ypos = yj * width;
					int tmp = (ods + yj) / 8 * scanlength+oms;
					for (int xj = 0; xj < width; xj++)
					{
						c = ((pixels[tmp + xj]>>b)&1);
						if(transparencyMask!=null) { c |= (((transparencyMask[tmp + xj]>>b)&1)^1)<<1; }
						data[(yj*width)+xj] = Type1[c];
					}
					b++;
					if(b>7) b=0;
				}

				temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				temp.setRGB(0, 0, width, height, data, 0, width);
				gc.drawImage(manipulateImage(temp, manipulation), x, y, null);
			break;

			case 1: // TYPE_BYTE_1_GRAY // used by Monkiki's Castles
				data = new int[pixels.length*8];

				for(int i=(offset/8); i<pixels.length; i++)
				{
					for(int j=7; j>=0; j--)
					{
						c = ((pixels[i]>>j)&1);
						if(transparencyMask!=null) { c |= (((transparencyMask[i]>>j)&1)^1)<<1; }
						data[(i*8)+(7-j)] = Type1[c];
					}
				}
				temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				temp.setRGB(0, 0, width, height, data, 0, scanlength);
				gc.drawImage(manipulateImage(temp, manipulation), x, y, null);
			break;

			default: System.out.println("drawPixels A : Format " + format + " Not Implemented");
		}
	}

	public void drawPixels(int[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format)
	{
		//System.out.println("drawPixels B "+format+" "+transparency); // Found In Use
		BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		temp.setRGB(0, 0, width, height, pixels, offset, scanlength);
		BufferedImage temp2 = manipulateImage(temp, manipulation);
		gc.drawImage(temp2, x, y, null);
	}

	public void drawPixels(short[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format)
	{
		//System.out.println("drawPixels C "+format+" "+transparency); // Found In Use
		int[] data = new int[pixels.length];
		for(int i=0; i<pixels.length; i++)
		{
			data[i] = pixelToColor(pixels[i], format);
			if(!transparency) { data[i] &=0x00FFFFFF; }
		}

		BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		temp.setRGB(0, 0, width, height, data, offset, scanlength);
		gc.drawImage(manipulateImage(temp, manipulation), x, y, null);
	}

	public void drawPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor)
	{
		int temp = color;
		int[] x = new int[nPoints];
		int[] y = new int[nPoints];

		setAlphaRGB(argbColor);

		for(int i=0; i<nPoints; i++)
		{
			x[i] = xPoints[xOffset+i];
			y[i] = yPoints[yOffset+i];
		}
		gc.drawPolygon(x, y, nPoints);
		setColor(temp);
	}

	public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor)
	{
		//System.out.println("drawTriange");
		int temp = color;
		setAlphaRGB(argbColor);
		gc.drawPolygon(new int[]{x1,x2,x3}, new int[]{y1,y2,y3}, 3);
		setColor(temp);
	}

	public void fillPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor)
	{
		int temp = color;
		int[] x = new int[nPoints];
		int[] y = new int[nPoints];

		setAlphaRGB(argbColor);

		for(int i=0; i<nPoints; i++)
		{
			x[i] = xPoints[xOffset+i];
			y[i] = yPoints[yOffset+i];
		}
		gc.fillPolygon(x, y, nPoints);
		setColor(temp);
	}

	public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3)
	{
		//System.out.println("fillTriangle"); // Found In Use
		gc.fillPolygon(new int[]{x1,x2,x3}, new int[]{y1,y2,y3}, 3);
	}

	public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor)
	{
		//System.out.println("fillTriangle"); // Found In Use
		int temp = color;
		setAlphaRGB(argbColor);
		gc.fillPolygon(new int[]{x1,x2,x3}, new int[]{y1,y2,y3}, 3);
		setColor(temp);
	}

	public void getPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int format)
	{
		System.out.println("getPixels A");
	}

	public void getPixels(int[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format)
	{
		//System.out.println("getPixels B");
		canvas.getRGB(x, y, width, height, pixels, offset, scanlength);
	}

	public void getPixels(short[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format)
	{
		//System.out.println("getPixels C"); // Found In Use
		int i = offset;
		for(int row=0; row<height; row++)
		{
			for (int col=0; col<width; col++)
			{
				pixels[i] = colorToShortPixel(canvas.getRGB(col+x, row+y), format);
				i++;
			}
		}
	}

	private int pixelToColor(short c, int format)
	{
		int a = 0xFF;
		int r = 0;
		int g = 0;
		int b = 0;
		switch(format)
		{
			case DirectGraphics.TYPE_USHORT_1555_ARGB:
				a = ((c>>15) & 0x01)*0xFF;
				r = (c>>10) & 0x1F; g = (c>>5) & 0x1F; b = c & 0x1F;
				r = (r<<3)|(r>>2); g = (g<<3)|(g>>2); b = (b<<3)|(b>>2);
				break;
			case DirectGraphics.TYPE_USHORT_444_RGB:
				r = (c>>8) & 0xF; g = (c>>4) & 0xF; b = c & 0xF;
				r = (r<<4)|r; g = (g<<4)|g; b = (b<<4)|b;
				break;
			case DirectGraphics.TYPE_USHORT_4444_ARGB:
				a = (c>>12) & 0xF; r = (c>>8) & 0xF; g = (c>>4) & 0xF; b = c & 0xF;
				a = (a<<4)|a; r = (r<<4)|r; g = (g<<4)|g; b = (b<<4)|b;
				break;
			case DirectGraphics.TYPE_USHORT_555_RGB:
				r = (c>>10) & 0x1F; g = (c>>5) & 0x1F; b = c & 0x1F;
				r = (r<<3)|(r>>2); g = (g<<3)|(g>>2); b = (b<<3)|(b>>2);
				break;
			case DirectGraphics.TYPE_USHORT_565_RGB:
				r = (c>>11) & 0x1F; g = (c>>5) & 0x3F; b = c & 0x1F;
				r = (r<<3)|(r>>2); g = (g<<2)|(g>>4); b = (b<<3)|(b>>2);
				break;
		}
		return (a<<24) | (r<<16) | (g<<8) | b;
	}

	private short colorToShortPixel(int c, int format)
	{
		int a = 0;
		int r = 0;
		int g = 0;
		int b = 0;
		int out = 0;
		switch(format)
		{
			case DirectGraphics.TYPE_USHORT_1555_ARGB:
				a=c>>>31; r=((c>>19)&0x1F); g=((c>>11)&0x1F); b=((c>>3)&0x1F);
				out=(a<<15)|(r<<10)|(g<<5)|b;
				break;
			case DirectGraphics.TYPE_USHORT_444_RGB:
				r=((c>>20)&0xF); g=((c>>12)&0xF); b=((c>>4)&0xF);
				out=(r<<8)|(g<<4)|b;
				break;
			case DirectGraphics.TYPE_USHORT_4444_ARGB:
				a=((c>>>28)&0xF); r=((c>>20)&0xF); g=((c>>12)&0xF); b=((c>>4)&0xF);
				out=(a<<12)|(r<<8)|(g<<4)|b;
				break;
			case DirectGraphics.TYPE_USHORT_555_RGB:
				r=((c>>19)&0x1F); g=((c>>11)&0x1F); b=((c>>3)&0x1F);
				out=(r<<10)|(g<<5)|b;
				break;
			case DirectGraphics.TYPE_USHORT_565_RGB:
				r=((c>>19)&0x1F); g=((c>>10)&0x3F); b=((c>>3)&0x1F);
				out=(r<<11)|(g<<5)|b;
				break;
		}
		return (short)out;
	}

	private BufferedImage manipulateImage(BufferedImage image, int manipulation)
	{
		final int HV = DirectGraphics.FLIP_HORIZONTAL | DirectGraphics.FLIP_VERTICAL;
		final int H90 = DirectGraphics.FLIP_HORIZONTAL | DirectGraphics.ROTATE_90;
		switch(manipulation)
		{
			case DirectGraphics.FLIP_HORIZONTAL:
				return PlatformImage.transformImage(image, Sprite.TRANS_MIRROR);
			case DirectGraphics.FLIP_VERTICAL: 
				return PlatformImage.transformImage(image, Sprite.TRANS_MIRROR_ROT180);
			case DirectGraphics.ROTATE_90: 
				return PlatformImage.transformImage(image, Sprite.TRANS_ROT270);
			case DirectGraphics.ROTATE_180:
				return PlatformImage.transformImage(image, Sprite.TRANS_ROT180);
			case DirectGraphics.ROTATE_270:
				return PlatformImage.transformImage(image, Sprite.TRANS_ROT90);
			case HV:
				return PlatformImage.transformImage(image, Sprite.TRANS_ROT180);
			case H90: 
				return PlatformImage.transformImage(PlatformImage.transformImage(image, Sprite.TRANS_MIRROR), Sprite.TRANS_ROT270);
			case 0: /* No Manipulation */ break;
			default:
				System.out.println("manipulateImage "+manipulation+" not defined");
		}
		return image;
	}

}

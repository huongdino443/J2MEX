import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.IndexBuffer;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;

public final class M3GRendererSmoke
{
public static void main(String[] args) throws Exception
	{
		CaptureGraphics target=new CaptureGraphics(64,64);
		Graphics3D graphics=new Graphics3D();
		graphics.bindTarget(target,true,Graphics3D.OVERWRITE);
		graphics.setViewport(0,0,64,64);

		Camera camera=new Camera();
		camera.setPerspective(90,1,0.1f,100);
		graphics.setCamera(camera,new Transform());
		Background background=new Background();
		background.setColor(0xff000000);
		background.setColorClearEnable(true);
		background.setDepthClearEnable(true);
		graphics.clear(background);

		VertexBuffer near=triangle(-2,0xffffffff);
		byte[] gray={ (byte)128,(byte)128,(byte)128, (byte)128,(byte)128,(byte)128,
				(byte)128,(byte)128,(byte)128, (byte)128,(byte)128,(byte)128 };
		Image2D image=new Image2D(Image2D.RGB,2,2,gray);
		Texture2D texture=new Texture2D(image);
		Appearance textured=new Appearance();
		textured.setTexture(0,texture);
		textured.setPolygonMode(noCull());
		graphics.render(near,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),textured,new Transform());

		VertexBuffer far=triangle(-3,0xff0000ff);
		Appearance blue=new Appearance();
		blue.setPolygonMode(noCull());
		CompositingMode state=new CompositingMode();
		state.setDepthTestEnable(true);
		state.setDepthWriteEnable(true);
		blue.setCompositingMode(state);
		graphics.render(far,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),blue,new Transform());
		graphics.releaseTarget();

		int center=target.pixels[32*64+32];
		int red=(center>>>16)&255, green=(center>>>8)&255, blueChannel=center&255;
		if (red<100||green<100||blueChannel<100)
			throw new AssertionError(String.format("Textured triangle missing: #%08x",center));
		if (blueChannel>red+20)
			throw new AssertionError(String.format("Depth test let the farther blue triangle overwrite: #%08x",center));
checkFrustumClipping(graphics,target,background);

		Transform transform=new Transform();
		transform.postTranslate(2,3,4);
		float[] matrix=new float[16];
		transform.get(matrix);
if (matrix[3]!=2||matrix[7]!=3||matrix[11]!=4||matrix[12]!=0||matrix[13]!=0||matrix[14]!=0)
throw new AssertionError("JSR-184 public matrices must be row-major");
transform.postRotate(37,0,1,0);
transform.get(matrix);
Transform roundtrip=new Transform();
roundtrip.set(matrix);
float[] actual=new float[16];
roundtrip.get(actual);
for (int i=0;i<16;i++) if (Math.abs(matrix[i]-actual[i])>0.00001f)
throw new AssertionError("Public matrix set/get roundtrip failed");
Transform inverse=new Transform(roundtrip);
inverse.invert();
roundtrip.postMultiply(inverse);
roundtrip.get(actual);
for (int i=0;i<16;i++) if (Math.abs(actual[i]-(i%5==0?1:0))>0.0001f)
throw new AssertionError("Transform copying/inversion mixed matrix layouts");

// Exercise the scene-graph path, not only direct render() with an identity camera.
graphics.bindTarget(target,true,0);
graphics.clear(background);
Transform cameraWorld=new Transform();
cameraWorld.postTranslate(2,3,4);
graphics.setCamera(camera,cameraWorld);
Mesh mesh=new Mesh(triangle(-2,0xff00ff00),
new TriangleStripArray(new int[]{0,1,2},new int[]{3}),blue);
mesh.setTranslation(2,3,4);
javax.microedition.m3g.Group root=new javax.microedition.m3g.Group();
root.addChild(mesh);
graphics.render(root,null);
graphics.releaseTarget();
if ((target.pixels[32*64+32]&0xffffff)!=0x00ff00)
throw new AssertionError("Translated camera/scene-graph mesh did not render at the center");
checkTextureTile(graphics,target,camera,background,1,1,Texture2D.FILTER_NEAREST,0xff0000);
checkTextureTile(graphics,target,camera,background,1,3,Texture2D.FILTER_NEAREST,0x0000ff);
checkTextureTile(graphics,target,camera,background,5,1,Texture2D.FILTER_LINEAR,0xff0000);
checkParallelCamera(graphics,target,background);
if (args.length>0&&!args[0].isEmpty()) checkMinecraftFrustum(args[0]);
System.out.println("M3GRendererSmoke: frustum clipping, atlas orientation, texel centers, depth, camera and scene graph passed");
	}

private static void checkFrustumClipping(Graphics3D graphics,CaptureGraphics target,Background background)
{
Camera camera=new Camera();
camera.setPerspective(90,1,1,10);
checkClippedTriangle(graphics,target,background,camera,
new short[]{-2,-2,-1, 2,-2,-4, 0,2,-4},0.5f,"near");
camera.setPerspective(90,1,1,2);
checkClippedTriangle(graphics,target,background,camera,
new short[]{-2,-2,-3, 2,-2,-3, 0,2,-6},0.5f,"far");
camera.setPerspective(90,1,1,10);
checkClippedTriangle(graphics,target,background,camera,
new short[]{-3,-1,-2, 1,-1,-2, 0,1,-2},1f,"left");
checkClippedTriangle(graphics,target,background,camera,
new short[]{3,-1,-2, -1,-1,-2, 0,1,-2},1f,"right");
checkClippedTriangle(graphics,target,background,camera,
new short[]{-1,-3,-2, 1,-1,-2, 0,1,-2},1f,"bottom");
checkClippedTriangle(graphics,target,background,camera,
new short[]{-1,3,-2, -1,-1,-2, 1,-1,-2},1f,"top");
}

private static void checkClippedTriangle(Graphics3D graphics,CaptureGraphics target,
Background background,Camera camera,short[] coordinates,float scale,String plane)
{
VertexArray positions=new VertexArray(3,3,VertexArray.SHORT);
positions.set(0,3,coordinates);
VertexBuffer crossing=new VertexBuffer();
crossing.setPositions(positions,scale,new float[]{0,0,0});
crossing.setDefaultColor(0xffff0000);
Appearance appearance=new Appearance();
appearance.setPolygonMode(noCull());
graphics.bindTarget(target,true,0);
graphics.clear(background);
graphics.setCamera(camera,new Transform());
graphics.render(crossing,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),
appearance,new Transform());
graphics.releaseTarget();
int visible=0;
for (int pixel:target.pixels) if ((pixel&0x00ffffff)!=0) visible++;
if (visible<20)
throw new AssertionError("Triangle crossing the "+plane+" clip plane was discarded: "+visible+" visible pixels");
}

private static void checkTextureTile(Graphics3D graphics,CaptureGraphics target,Camera camera,
Background background,int u,int v,int filter,int expected)
{
Image2D image=new Image2D(Image2D.RGB,2,2,new byte[]{
(byte)255,0,0, 0,(byte)255,0, 0,0,(byte)255, (byte)255,(byte)255,(byte)255});
Texture2D texture=new Texture2D(image);
texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,filter);
texture.setWrapping(Texture2D.WRAP_REPEAT,Texture2D.WRAP_REPEAT);
VertexBuffer triangle=triangle(-2,0xffffffff);
VertexArray coords=new VertexArray(3,2,VertexArray.BYTE);
coords.set(0,3,new byte[]{(byte)u,(byte)v,(byte)u,(byte)v,(byte)u,(byte)v});
triangle.setTexCoords(0,coords,0.25f,null);
Appearance appearance=new Appearance();
appearance.setPolygonMode(noCull()); appearance.setTexture(0,texture);
graphics.bindTarget(target,true,0); graphics.clear(background);
graphics.setCamera(camera,new Transform());
graphics.render(triangle,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),appearance,null);
graphics.releaseTarget();
int actual=target.pixels[32*64+32]&0xffffff;
if (actual!=expected) throw new AssertionError(String.format(
"Wrong atlas texel at (%d/4,%d/4), filter %d: #%06x expected #%06x",u,v,filter,actual,expected));
}

private static void checkParallelCamera(Graphics3D graphics,CaptureGraphics target,Background background)
{
Camera camera=new Camera(); camera.setParallel(10,1,-10,10);
Transform projection=new Transform(); camera.getProjection(projection);
float[] matrix=new float[16]; projection.get(matrix);
if(Math.abs(matrix[0]-0.2f)>0.0001f||Math.abs(matrix[5]-0.2f)>0.0001f)
throw new AssertionError("Parallel camera height was treated as an angle");
graphics.bindTarget(target,true,0); graphics.clear(background); graphics.setCamera(camera,null);
Appearance appearance=new Appearance(); appearance.setPolygonMode(noCull());
TriangleStripArray indices=new TriangleStripArray(new int[]{0,1,2},new int[]{3});
graphics.render(triangle(-2,0xffff0000),indices,appearance,null);
graphics.render(triangle(-3,0xff0000ff),indices,appearance,null);
graphics.releaseTarget();
if((target.pixels[32*64+32]&0xffffff)!=0xff0000) throw new AssertionError("Orthographic depth ordering failed");
}

private static void checkMinecraftFrustum(String jar) throws Exception
{
try (java.net.URLClassLoader loader=new java.net.URLClassLoader(
new java.net.URL[]{new java.io.File(jar).toURI().toURL()},M3GRendererSmoke.class.getClassLoader())) {
Class<?> type=loader.loadClass("Frustum");
java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor();
constructor.setAccessible(true);
Object frustum=constructor.newInstance();
java.lang.reflect.Method set=type.getDeclaredMethod("set",Transform.class,float.class,float.class);
set.setAccessible(true);
Transform camera=new Transform();
camera.postTranslate(100,50,200);
camera.postRotate(180,0,1,0);
set.invoke(frustum,camera,90f,1f);
String[] names={"ax","ay","az"};
float[] expected={100,50,200};
for (int i=0;i<names.length;i++) {
java.lang.reflect.Field field=type.getDeclaredField(names[i]); field.setAccessible(true);
if (Math.abs(field.getFloat(frustum)-expected[i])>0.001f)
throw new AssertionError("Original Minecraft Frustum received wrong camera "+names[i]);
}
java.lang.reflect.Method visible=type.getDeclaredMethod("visible",
float.class,float.class,float.class,float.class,float.class,float.class,float.class);
visible.setAccessible(true);
if (!((Boolean)visible.invoke(frustum,99f,49f,204f,101f,51f,206f,100f)))
throw new AssertionError("Original Minecraft Frustum culled terrain in front of camera");
if (((Boolean)visible.invoke(frustum,99f,49f,190f,101f,51f,192f,100f)))
throw new AssertionError("Original Minecraft Frustum accepted terrain behind camera");
System.out.println("Minecraft Frustum: original JAR camera position and terrain visibility passed");
}
}

	private static VertexBuffer triangle(float z,int color)
	{
		VertexArray positions=new VertexArray(3,3,VertexArray.SHORT);
		positions.set(0,3,new short[]{-1,-1,(short)z, 1,-1,(short)z, 0,1,(short)z});
		VertexBuffer buffer=new VertexBuffer();
		buffer.setPositions(positions,1,new float[]{0,0,0});
		buffer.setDefaultColor(color);
		return buffer;
	}

	private static PolygonMode noCull()
	{
		PolygonMode mode=new PolygonMode();
		mode.setCulling(PolygonMode.CULL_NONE);
		return mode;
	}

	private static final class CaptureGraphics extends Graphics
	{
		final int width,height;
		final int[] pixels;
		CaptureGraphics(int width,int height) { this.width=width; this.height=height; pixels=new int[width*height]; }
		@Override public int getClipX() { return 0; }
		@Override public int getClipY() { return 0; }
		@Override public int getClipWidth() { return width; }
		@Override public int getClipHeight() { return height; }
		@Override public void drawRGB(int[] rgbData,int offset,int scanlength,int x,int y,int width,int height,boolean processAlpha)
		{
			for (int row=0;row<height;row++)
				System.arraycopy(rgbData,offset+row*scanlength,pixels,(y+row)*this.width+x,width);
		}
	}
}

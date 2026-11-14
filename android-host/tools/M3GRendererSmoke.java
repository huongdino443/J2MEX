import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Fog;
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
		checkDefaultTextureWrapping(graphics,target,camera,background);
checkTextureTransform(graphics,target,background);
checkMipmapFiltering(graphics,target,background);
checkPerspectiveSamplingWithFastestHint(graphics,target,background);
		checkSeamlessClampFallback(graphics,target,background);
checkParallelCamera(graphics,target,background);
checkFogUsesInterpolatedEyeDepth(graphics,target,background);
if (args.length>0&&!args[0].isEmpty()) checkMinecraftFrustum(args[0]);
		System.out.println("M3GRendererSmoke: texture transforms, default repeat wrapping, mipmapping, seamless UV wrapping, frustum clipping, atlas sampling, eye-space fog, depth, camera and scene graph passed");
	}

private static void checkDefaultTextureWrapping(Graphics3D graphics,CaptureGraphics target,
Camera camera,Background background)
{
	// The J2ME Loader native M3G implementation defaults new Texture2D objects
	// to REPEAT. Keep this image's opposite edges different so the renderer's
	// seamless-edge heuristic cannot mask an incorrect CLAMP default.
	byte[] pixels={
		(byte)255,0,0, 0,0,(byte)255,
		(byte)255,0,0, 0,0,(byte)255
	};
	Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,2,2,pixels));
	if (texture.getWrappingS()!=Texture2D.WRAP_REPEAT
		|| texture.getWrappingT()!=Texture2D.WRAP_REPEAT)
		throw new AssertionError("Texture2D must default to J2ME Loader's REPEAT wrapping");
	texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
	VertexBuffer vertices=triangle(-2,0xffffffff);
	VertexArray coords=new VertexArray(3,2,VertexArray.BYTE);
	coords.set(0,3,new byte[]{5,1,5,1,5,1});
	vertices.setTexCoords(0,coords,0.25f,null);
	Appearance appearance=new Appearance();
	appearance.setPolygonMode(noCull());
	appearance.setTexture(0,texture);
	graphics.bindTarget(target,true,0);
	graphics.clear(background);
	graphics.setCamera(camera,new Transform());
	graphics.render(vertices,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),
		appearance,null);
	graphics.releaseTarget();
	int actual=target.pixels[32*64+32]&0xffffff;
	if (actual!=0xff0000)
		throw new AssertionError(String.format(
			"Default texture did not repeat U=1.25 to the first texel: #%06x",actual));
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

private static void checkTextureTransform(Graphics3D graphics,CaptureGraphics target,
Background background)
{
Camera camera=new Camera(); camera.setPerspective(90,1,0.1f,100);
Image2D image=new Image2D(Image2D.RGB,2,2,new byte[]{
(byte)255,0,0, 0,(byte)255,0, 0,0,(byte)255, (byte)255,(byte)255,(byte)255});
Texture2D texture=new Texture2D(image);
texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
texture.setWrapping(Texture2D.WRAP_REPEAT,Texture2D.WRAP_REPEAT);
VertexBuffer triangle=triangle(-2,0xffffffff);
VertexArray coords=new VertexArray(3,2,VertexArray.BYTE);
coords.set(0,3,new byte[]{1,1,1,1,1,1});
triangle.setTexCoords(0,coords,0.25f,null);
Appearance appearance=new Appearance();
appearance.setPolygonMode(noCull()); appearance.setTexture(0,texture);
graphics.bindTarget(target,true,0); graphics.clear(background);
graphics.setCamera(camera,new Transform());
graphics.render(triangle,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),appearance,null);
int before=target.pixels[32*64+32]&0xffffff;
texture.translate(0.5f,0,0);
graphics.clear(background);
graphics.render(triangle,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),appearance,null);
graphics.releaseTarget();
int after=target.pixels[32*64+32]&0xffffff;
if (before!=0xff0000||after!=0x00ff00)
throw new AssertionError(String.format("Texture2D translation was not applied: before #%06x after #%06x",before,after));
}

private static void checkMipmapFiltering(Graphics3D graphics,CaptureGraphics target,
Background background)
{
Camera camera=new Camera(); camera.setPerspective(90,1,0.1f,100);
byte[] checker=new byte[16*16*3];
for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
byte value=(byte)(((x+y)&1)==0?255:0);
int index=(y*16+x)*3;
checker[index]=checker[index+1]=checker[index+2]=value;
}
Image2D image=new Image2D(Image2D.RGB,16,16,checker);
Texture2D texture=new Texture2D(image);
texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
texture.setWrapping(Texture2D.WRAP_REPEAT,Texture2D.WRAP_REPEAT);
VertexBuffer triangle=triangle(-2,0xffffffff);
VertexArray coords=new VertexArray(3,2,VertexArray.SHORT);
coords.set(0,3,new short[]{0,0, 8,0, 4,8});
triangle.setTexCoords(0,coords,1f,null);
Appearance appearance=new Appearance();
appearance.setPolygonMode(noCull()); appearance.setTexture(0,texture);
TriangleStripArray indices=new TriangleStripArray(new int[]{0,1,2},new int[]{3});
graphics.bindTarget(target,true,0); graphics.clear(background);
graphics.setCamera(camera,new Transform());
graphics.render(triangle,indices,appearance,null);
int base=target.pixels[32*64+32]&0xffffff;
texture.setFiltering(Texture2D.FILTER_NEAREST,Texture2D.FILTER_NEAREST);
graphics.clear(background);
graphics.render(triangle,indices,appearance,null);
graphics.releaseTarget();
int mip=target.pixels[32*64+32]&0xffffff;
int gray=(mip>>>16)&255;
if (base<220||gray<115||gray>140)
throw new AssertionError(String.format("Mipmap minification filter failed: base #%06x mip #%06x",base,mip));
}

private static void checkPerspectiveSamplingWithFastestHint(Graphics3D graphics,
CaptureGraphics target,Background background)
{
	Camera camera=new Camera(); camera.setPerspective(90,1,0.1f,100);
	byte[] texels={
		(byte)255,0,0,
		0,(byte)255,0,
		0,0,(byte)255,
		(byte)255,(byte)255,(byte)255
	};
	Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,4,1,texels));
	texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
	VertexArray positions=new VertexArray(3,3,VertexArray.SHORT);
	positions.set(0,3,new short[]{-1,-1,-2, 4,-4,-8, 0,4,-4});
	VertexBuffer vertices=new VertexBuffer();
	vertices.setPositions(positions,1f,null);
	vertices.setDefaultColor(0xffffffff);
	VertexArray coordinates=new VertexArray(3,2,VertexArray.SHORT);
	coordinates.set(0,3,new short[]{0,16384, 32767,16384, 0,16384});
	vertices.setTexCoords(0,coordinates,1f/32767f,null);
	PolygonMode mode=noCull();
	mode.setPerspectiveCorrectionEnable(false);
	Appearance appearance=new Appearance();
	appearance.setPolygonMode(mode);
	appearance.setTexture(0,texture);
	graphics.bindTarget(target,true,0);
	graphics.clear(background);
	graphics.setCamera(camera,new Transform());
	graphics.render(vertices,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),
		appearance,null);
	graphics.releaseTarget();
	int actual=target.pixels[32*64+32]&0xffffff;
	if(actual!=0xff0000)
		throw new AssertionError(String.format(
			"GL_FASTEST hint incorrectly disabled perspective texture interpolation: #%06x",actual));
}

private static void checkSeamlessClampFallback(Graphics3D graphics,CaptureGraphics target,
		Background background)
{
	Camera camera=new Camera(); camera.setPerspective(90,1,0.1f,100);
	byte[] tile=new byte[16*16*3],nonTile=new byte[16*16*3];
	for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
		int index=(y*16+x)*3;
		tile[index]=(byte)(x==0||x==15?255:0);
		nonTile[index]=(byte)(x*16);
	}
	VertexBuffer vertices=triangle(-2,0xffffffff);
	VertexArray coords=new VertexArray(3,2,VertexArray.SHORT);
	coords.set(0,3,new short[]{0,0,0,0,0,0});
	vertices.setTexCoords(0,coords,1,new float[]{1.25f,0.5f});
	Appearance appearance=new Appearance();
	appearance.setPolygonMode(noCull());
	TriangleStripArray indices=new TriangleStripArray(new int[]{0,1,2},new int[]{3});
	graphics.bindTarget(target,true,0);
	graphics.clear(background);
	graphics.setCamera(camera,new Transform());
	Texture2D tileTexture=new Texture2D(new Image2D(Image2D.RGB,16,16,tile));
	tileTexture.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
	tileTexture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
	appearance.setTexture(0,tileTexture);
	graphics.render(vertices,indices,appearance,new Transform());
	graphics.releaseTarget();
	int tiled=target.pixels[32*64+32];
	Texture2D clampTexture=new Texture2D(new Image2D(Image2D.RGB,16,16,nonTile));
	clampTexture.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
	clampTexture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
	appearance.setTexture(0,clampTexture);
	graphics.bindTarget(target,true,0);
	graphics.clear(background);
	graphics.setCamera(camera,new Transform());
	graphics.render(vertices,indices,appearance,new Transform());
	graphics.releaseTarget();
	int clamped=target.pixels[32*64+32];
	if(((tiled>>>16)&255)>8||((clamped>>>16)&255)<220)
		throw new AssertionError(String.format(
				"Seamless out-of-range UV fallback failed: tile #%08x non-tile #%08x",
				tiled,clamped));

	// A sampler's wrap mode is fixed for a draw. An inferred tile must not
	// switch back to clamp exactly at an integer UV seam.
	byte[] nearSeam={100,0,0, 108,0,0, 100,0,0, 108,0,0};
	VertexBuffer seamVertices=triangle(-2,0xffffffff);
	VertexArray seamCoords=new VertexArray(3,2,VertexArray.SHORT);
	seamCoords.set(0,3,new short[]{50,50, 150,50, 100,50});
	seamVertices.setTexCoords(0,seamCoords,0.01f,null);
	Texture2D seamTexture=new Texture2D(new Image2D(Image2D.RGB,2,2,nearSeam));
	seamTexture.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
	seamTexture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
	appearance.setTexture(0,seamTexture);
	graphics.bindTarget(target,true,0);
	graphics.clear(background);
	graphics.setCamera(camera,new Transform());
	graphics.render(seamVertices,indices,appearance,new Transform());
	graphics.releaseTarget();
	int wrappedSeam=target.pixels[32*64+32];
	if(((wrappedSeam>>>16)&255)>103)
		throw new AssertionError(String.format(
				"Inferred repeat changed to clamp at UV=1: #%08x",wrappedSeam));

	// The same continuous-edged image remains clamped when this mesh never tiles.
	VertexArray clampCoords=new VertexArray(3,2,VertexArray.SHORT);
	clampCoords.set(0,3,new short[]{100,50, 100,50, 100,50});
	seamVertices.setTexCoords(0,clampCoords,0.01f,null);
	graphics.bindTarget(target,true,0);
	graphics.clear(background);
	graphics.setCamera(camera,new Transform());
	graphics.render(seamVertices,indices,appearance,new Transform());
	graphics.releaseTarget();
	int clampedSeam=target.pixels[32*64+32];
	if(((clampedSeam>>>16)&255)<105)
		throw new AssertionError(String.format(
				"Ordinary CLAMP sampling was changed: #%08x",clampedSeam));
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

private static void checkFogUsesInterpolatedEyeDepth(Graphics3D graphics,CaptureGraphics target,
Background background)
{
	Camera camera=new Camera();
	camera.setPerspective(90,1,0.1f,100);
	VertexArray positions=new VertexArray(3,3,VertexArray.SHORT);
	positions.set(0,3,new short[]{
		-1,-1,(short)-2,
		 3,-2,(short)-4,
		 0, 6,(short)-8
	});
	VertexBuffer vertices=new VertexBuffer();
	vertices.setPositions(positions,1,new float[]{0,0,0});
	vertices.setDefaultColor(0xffffffff);
	Appearance appearance=new Appearance();
	appearance.setPolygonMode(noCull());
	Fog fog=new Fog();
	if(fog.getMode()!=Fog.LINEAR||fog.getDensity()!=1f
			||fog.getNearDistance()!=0f||fog.getFarDistance()!=1f)
		throw new AssertionError("New Fog did not match J2ME Loader's LINEAR defaults");
	fog.setColor(0x000000);
	fog.setLinear(0,9);
	appearance.setFog(fog);
	graphics.bindTarget(target,true,0);
	graphics.clear(background);
	graphics.setCamera(camera,new Transform());
	graphics.render(vertices,new TriangleStripArray(new int[]{0,1,2},new int[]{3}),
		appearance,new Transform());
	graphics.releaseTarget();
	int pixel=target.pixels[32*64+32]&0xffffff;
	// The center sample is near the triangle centroid; screen-linear eye depth
	// produces a gray value around 118. Perspective-correct depth would be much
	// nearer white and would fail this check.
	if(Math.abs(((pixel>>>16)&255)-118)>8
			||Math.abs(((pixel>>>8)&255)-118)>8
			||Math.abs((pixel&255)-118)>8)
		throw new AssertionError(String.format(
			"Fog did not use linearly interpolated eye-space depth: #%06x",pixel));
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

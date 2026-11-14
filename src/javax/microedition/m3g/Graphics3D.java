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

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.recompile.mobile.Mobile;

public class Graphics3D
{

	public static final int ANTIALIAS = 2;
	public static final int DITHER = 4;
	public static final int OVERWRITE = 16;
	public static final int TRUE_COLOR = 8;


	private static final Hashtable properties = new Hashtable();

	static
	{
		properties.put("supportAntialiasing", "false");
		properties.put("supportTrueColor", "true");
		properties.put("supportDithering", "false");
	}

	private int viewx;
	private int viewy;
	private int vieww;
	private int viewh;
	private float near;
private float far=1;
	private int hints;
	private boolean depthBuffer;
	private Object target;
	private Camera camera;
	private final Transform cameraTransform = new Transform();
	private final M3GSoftwareRenderer renderer = new M3GSoftwareRenderer();
	private final List<Light> lights = new ArrayList<Light>();
	private final List<Transform> lightTransforms = new ArrayList<Transform>();
	private final IdentityHashMap<Image2D,Boolean> partialAlphaImageCache =
			new IdentityHashMap<Image2D,Boolean>();
	private final List<DeferredDirectDraw> deferredDirectDraws = new ArrayList<DeferredDirectDraw>();
private final float[] cameraTraceCurrent = new float[16];
private final float[] cameraTracePrevious = new float[16];
private boolean hasCameraTrace;
private int cameraTraceCount;
private final float[] cityCameraTraceCurrent = new float[16];
private int cityCameraTraceCalls;
private int cityCameraTraceCount;
private int cityNodeSampleBudget;
private int cityNodeSampleWindows;
private int cityNodeSampleIndex;
private int cityNodeTraceCount;
private int theFightCameraTraceCount;
private long theFightCameraTraceLastMs;


	public int addLight(Light light, Transform transform)
	{
		if (light == null) throw new NullPointerException("light");
		lights.add(light);
		lightTransforms.add(transform==null?new Transform():new Transform(transform));
		return lights.size()-1;
	}

public void bindTarget(java.lang.Object target) { bindTarget(target, true, 0); }

	public void bindTarget(java.lang.Object target, boolean depthBuffer, int hints)
	{
		if (this.target != null) throw new IllegalStateException("An M3G target is already bound");
renderer.bind(target,depthBuffer);
		deferredDirectDraws.clear();
this.target=target;
		this.depthBuffer=depthBuffer;
		this.hints=hints;
if (target instanceof javax.microedition.lcdui.Graphics) {
			javax.microedition.lcdui.Graphics g=(javax.microedition.lcdui.Graphics)target;
			viewx=g.getClipX(); viewy=g.getClipY();
			vieww=Math.max(1,g.getClipWidth()); viewh=Math.max(1,g.getClipHeight());
		}
	}

	public void clear(Background background) { renderer.clear(background); }

	public Camera getCamera(Transform transform) { return renderer.camera(transform); }

	public float getDepthRangeFar() { return far; }

	public float getDepthRangeNear() { return near; }

	public int getHints() { return hints; }

	public static Graphics3D getInstance() { return Mobile.getGraphics3D(); }

	public Light getLight(int index, Transform transform)
	{
		Light light=lights.get(index);
		if (transform!=null) transform.set(lightTransforms.get(index));
		return light;
	}

	public int getLightCount() { return lights.size(); }

	public static Hashtable getProperties() { return properties; }

	public Object getTarget() { return target; }

	public int getViewportHeight() { return viewh; }

	public int getViewportWidth() { return vieww; }

	public int getViewportX() { return viewx; }

	public int getViewportY() { return viewy; }

	public boolean isDepthBufferEnabled() { return depthBuffer; }

	public void releaseTarget()
	{
		renderDeferredDirectDraws();
		renderer.release();
		target=null;
	}

	public void render(Node node, Transform transform)
	{
		if (target==null) throw new IllegalStateException("No M3G target is bound");
		if (node==null) throw new NullPointerException("node");
		Transform parent=transform==null?new Transform():new Transform(transform);
		List<M3GSoftwareRenderer.LightSource> activeLights=contextLightSources();
		collectLights(node,parent,false,activeLights);
		renderer.setLights(activeLights);
traceCityBloxxNode(node,parent,false);
		List<DeferredDraw> translucent=new ArrayList<DeferredDraw>();
		List<DeferredDraw> additive=new ArrayList<DeferredDraw>();
		renderNode(node,parent,false,translucent,additive);
		renderTranslucent(translucent);
		renderAdditive(additive);
	}

	public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform)
	{
		render(vertices,triangles,appearance,transform,0);
	}

	public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform, int scope)
	{
		if (target==null) throw new IllegalStateException("No M3G target is bound");
		renderer.setLights(contextLightSources());
		CompositingMode mode=appearance==null?null:appearance.getCompositingMode();
		if (mode!=null&&!mode.isDepthWriteEnabled()
				&&requiresDeferredAlphaPass(appearance,vertices,null)) {
			deferredDirectDraws.add(new DeferredDirectDraw(vertices,triangles,appearance,transform,
					camera,cameraTransform,viewx,viewy,vieww,viewh,contextLightSources()));
			return;
		}
		renderer.render(vertices,triangles,appearance,transform);
	}

	private void renderDeferredDirectDraws()
	{
		if (deferredDirectDraws.isEmpty()) return;
		for (DeferredDirectDraw call:deferredDirectDraws) {
			renderer.setViewport(call.viewportX,call.viewportY,call.viewportWidth,call.viewportHeight);
			renderer.setCamera(call.camera,call.cameraTransform);
			renderer.setLights(call.lights);
			renderer.render(call.vertices,call.triangles,call.appearance,call.transform);
		}
		deferredDirectDraws.clear();
		renderer.setViewport(viewx,viewy,vieww,viewh);
		renderer.setCamera(camera,cameraTransform);
		renderer.setLights(contextLightSources());
	}

	public void render(World world)
	{
		if (world==null) throw new NullPointerException("world");
		clear(world.getBackground());
		Camera active=world.getActiveCamera();
		if (active!=null) {
			Transform transform=new Transform();
			active.getCompositeTransform(transform);
traceCameraTransform(transform);
			setCamera(active,transform);
		}
		List<M3GSoftwareRenderer.LightSource> activeLights=contextLightSources();
		collectLights(world,new Transform(),false,activeLights);
		renderer.setLights(activeLights);
		List<DeferredDraw> translucent=new ArrayList<DeferredDraw>();
		List<DeferredDraw> additive=new ArrayList<DeferredDraw>();
		renderNode(world,new Transform(),false,translucent,additive);
		renderTranslucent(translucent);
		renderAdditive(additive);
	}

private void traceCameraTransform(Transform transform)
{
if (cameraTraceCount>=12) return;
transform.getColumnMajor(cameraTraceCurrent);
boolean changed=!hasCameraTrace;
if (!changed) {
for (int i=0;i<16;i++) {
if (Math.abs(cameraTraceCurrent[i]-cameraTracePrevious[i])>0.0001f) {
changed=true;
break;
}
}
}
if (!changed) return;
System.arraycopy(cameraTraceCurrent,0,cameraTracePrevious,0,16);
hasCameraTrace=true;
cameraTraceCount++;
 System.out.println("M3G camera transform #"+cameraTraceCount+" position=("+
 cameraTraceCurrent[12]+","+cameraTraceCurrent[13]+","+cameraTraceCurrent[14]+")");
 }

private void traceCityBloxxCameraTransform(Transform transform)
{
cityCameraTraceCalls++;
boolean firstSamples=cityCameraTraceCalls<=12;
boolean periodicSample=cityCameraTraceCalls%300==0;
if (!firstSamples && !periodicSample) return;
if (cityCameraTraceCount>=48) return;
transform.getColumnMajor(cityCameraTraceCurrent);
float[] projection=new float[4];
int projectionType=camera==null?-1:camera.getProjection(projection);
cityCameraTraceCount++;
System.out.println("[CityBloxx-M3G] camera sample #"+cityCameraTraceCount
 + " setCameraCall="+cityCameraTraceCalls
 + " position=("+cityCameraTraceCurrent[12]+","+cityCameraTraceCurrent[13]+","+cityCameraTraceCurrent[14]+")"
 + " matrix="+java.util.Arrays.toString(cityCameraTraceCurrent)
 + " projectionType="+projectionType+" projection="+java.util.Arrays.toString(projection));
if (cityCameraTraceCalls%600==0 && cityNodeSampleWindows<8) {
cityNodeSampleBudget=24;
cityNodeSampleWindows++;
cityNodeSampleIndex=0;
}
}

private synchronized void traceCityBloxxNode(Node node, Transform parent, boolean applyLocalTransform)
{
if (!(node instanceof Mesh)) return;
boolean initialSample=cityNodeTraceCount<24;
if (!initialSample && cityNodeSampleBudget<=0) return;
try {
 if (Mobile.getPlatform()==null || Mobile.getPlatform().loader==null
 || !"House".equals(Mobile.getPlatform().loader.getMIDletClassName())) return;
Mesh mesh=(Mesh)node;
int nodeSequence=initialSample?cityNodeTraceCount+1:cityNodeSampleIndex+1;
Transform local=new Transform();
node.getLocalCompositeTransform(local);
Transform world=new Transform(parent);
 if (applyLocalTransform) world.postMultiply(local);
float[] parentMatrix=new float[16];
float[] localMatrix=new float[16];
float[] worldMatrix=new float[16];
parent.getColumnMajor(parentMatrix);
local.getColumnMajor(localMatrix);
world.getColumnMajor(worldMatrix);
if (initialSample) cityNodeTraceCount++;
else {
cityNodeSampleBudget--;
cityNodeSampleIndex++;
}
 System.out.println("[CityBloxx-M3G] node #"+cityNodeTraceCount
 + " sample="+(initialSample?"initial":cityNodeSampleWindows+":"+nodeSequence)
 + " userID="+mesh.getUserID()+" submeshes="+mesh.getSubmeshCount()
+ " viewport="+viewx+","+viewy+","+vieww+","+viewh
+ " camera="+(camera==null?"none":camera.getClass().getSimpleName()));
System.out.println("[CityBloxx-M3G] parent="+java.util.Arrays.toString(parentMatrix));
System.out.println("[CityBloxx-M3G] local="+java.util.Arrays.toString(localMatrix));
 System.out.println("[CityBloxx-M3G] rootLocalApplied="+applyLocalTransform);
System.out.println("[CityBloxx-M3G] model="+java.util.Arrays.toString(worldMatrix));
} catch (Throwable traceFailure) {
System.out.println("[CityBloxx-M3G] trace failed: "+traceFailure);
}
}

	public void resetLights() { lights.clear(); lightTransforms.clear(); }

	public void setCamera(Camera camera, Transform transform)
	{
		this.camera=camera;
		if (transform==null && camera!=null) camera.getCompositeTransform(cameraTransform);
		else if (transform!=null) cameraTransform.set(transform);
		renderer.setCamera(camera,cameraTransform);
		traceTheFightCamera(camera,cameraTransform);
		if (Mobile.getPlatform()!=null && Mobile.getPlatform().loader!=null
				&& "House".equals(Mobile.getPlatform().loader.getMIDletClassName()))
			traceCityBloxxCameraTransform(cameraTransform);
	}

	private void traceTheFightCamera(Camera camera, Transform transform)
	{
		if (camera==null || transform==null || theFightCameraTraceCount>=60) return;
		if (Mobile.getPlatform()==null || Mobile.getPlatform().loader==null) return;
		String gameName=Mobile.getPlatform().loader.name;
		if (gameName==null || !gameName.toLowerCase(java.util.Locale.ROOT).contains("thefight3d")) return;
		long now=System.currentTimeMillis();
		if (theFightCameraTraceCount>0 && now-theFightCameraTraceLastMs<1000) return;

		float[] world=new float[16];
		transform.getColumnMajor(world);
		float[] projectionParams=new float[4];
		int projectionType=camera.getProjection(projectionParams);
		String viewSummary;
		try {
			Transform view=new Transform(transform);
			view.invert();
			float[] viewMatrix=new float[16];
			view.getColumnMajor(viewMatrix);
			viewSummary=java.util.Arrays.toString(viewMatrix);
		} catch (ArithmeticException singular) {
			viewSummary="singular";
		}
		theFightCameraTraceCount++;
		theFightCameraTraceLastMs=now;
		System.out.println("[TheFight3D-camera] sample="+theFightCameraTraceCount
				+" viewport="+vieww+"x"+viewh
				+" projectionType="+projectionType
				+" fovy/aspect/near/far="+java.util.Arrays.toString(projectionParams)
				+" eye="+world[12]+","+world[13]+","+world[14]
				+" forward="+(-world[8])+","+(-world[9])+","+(-world[10])
				+" world="+java.util.Arrays.toString(world)
				+" view="+viewSummary);
	}

	public void setDepthRange(float Near, float Far)
	{
		if (Near<0||Far>1||Near>=Far) throw new IllegalArgumentException("Depth range must satisfy 0 <= near < far <= 1");
		near=Near; far=Far;
	}

	public void setLight(int index, Light light, Transform transform)
	{
		if (light==null) throw new NullPointerException("light");
		while (lights.size()<=index) lights.add(null);
		while (lightTransforms.size()<=index) lightTransforms.add(new Transform());
		lights.set(index,light);
		lightTransforms.set(index,transform==null?new Transform():new Transform(transform));
	}

	public void setViewport(int x, int y, int width, int height)
	{
		if (width<=0||height<=0) throw new IllegalArgumentException("Invalid viewport dimensions");
viewx=x; viewy=y; vieww=width; viewh=height;
		renderer.setViewport(x,y,width,height);
	}

	private void renderNode(Node node,Transform parent,boolean applyLocalTransform,
			List<DeferredDraw> translucent,List<DeferredDraw> additive)
	{
		if (!node.isRenderingEnabled()) return;
		Transform world=new Transform(parent);
		if (applyLocalTransform) {
			Transform local=new Transform();
			node.getLocalCompositeTransform(local);
			world.postMultiply(local);
		}
		if (node instanceof SkinnedMesh) {
			Group skeleton=((SkinnedMesh)node).getSkeleton();
			if (skeleton!=null) renderNode(skeleton,world,true,translucent,additive);
		}
		if (node instanceof Sprite3D) {
			Sprite3D sprite=(Sprite3D)node;
			if (isAdditive(sprite.getAppearance()))
				additive.add(new DeferredDraw(node,world,-1));
		else if (requiresDeferredAlphaPass(sprite.getAppearance(),null,sprite.getImage()))
				translucent.add(new DeferredDraw(node,world,-1));
			else
				renderer.render((Sprite3D)node,world);
		} else if (node instanceof Mesh) {
			Mesh mesh=(Mesh)node;
			for (int i=0;i<mesh.getSubmeshCount();i++) {
				if (isAdditive(mesh.getAppearance(i))) {
					additive.add(new DeferredDraw(node,world,i));
			} else if (requiresDeferredAlphaPass(mesh.getAppearance(i),mesh.getVertexBuffer(),null)) {
					translucent.add(new DeferredDraw(node,world,i));
				} else {
					renderSubmesh(mesh,i,world);
				}
			}
		}
		if (node instanceof Group) {
			Group group=(Group)node;
			for (Node child:group.children()) renderNode(child,world,true,translucent,additive);
		}
	}

	private boolean isAdditive(Appearance appearance)
	{
		CompositingMode mode=appearance==null?null:appearance.getCompositingMode();
		return mode!=null&&mode.getBlending()==CompositingMode.ALPHA_ADD;
	}

	private boolean requiresDeferredAlphaPass(Appearance appearance,VertexBuffer vertices,Image2D spriteImage)
	{
		CompositingMode mode=appearance==null?null:appearance.getCompositingMode();
		if (mode!=null&&mode.getBlending()!=CompositingMode.ALPHA) return false;

		Material material=appearance==null?null:appearance.getMaterial();
		VertexArray colors=vertices==null?null:vertices.getColors();
		if (colors==null||colors.getComponentCount()<3) {
			int defaultColor=vertices==null?0xffffffff:vertices.getDefaultColor();
			if (material!=null) defaultColor=material.getColor(Material.DIFFUSE);
			else if (hasTexture(appearance)) defaultColor=0xffffffff;
			if (isNonOpaqueAlpha((defaultColor>>>24)&255)) return true;
		} else if (colors.getComponentCount()>3) {
			for (int vertex=0;vertex<colors.getVertexCount();vertex++)
				if (isNonOpaqueAlpha((int)colors.unsignedComponent(vertex,3))) return true;
		}

		if (spriteImage!=null&&hasPartialAlpha(spriteImage)) return true;
		if (appearance!=null) {
			for (int unit=0;unit<8;unit++) {
				Texture2D texture=appearance.getTexture(unit);
				if (texture!=null&&hasPartialAlpha(texture.getImage())) return true;
			}
		}
		return false;
	}

	private boolean hasTexture(Appearance appearance)
	{
		if (appearance==null) return false;
		for (int unit=0;unit<8;unit++) if (appearance.getTexture(unit)!=null) return true;
		return false;
	}

	private boolean hasPartialAlpha(Image2D image)
	{
		if (image==null) return false;
		int format=image.getFormat();
		if (format==Image2D.RGB||format==Image2D.LUMINANCE) return false;
		if (!image.isMutable()) {
			Boolean cached=partialAlphaImageCache.get(image);
			if (cached!=null) return cached.booleanValue();
		}
		int[] pixels=image.pixels();
		boolean partial=pixels==null;
		if (pixels!=null) {
			for (int pixel:pixels) {
				if (isNonOpaqueAlpha((pixel>>>24)&255)) { partial=true; break; }
			}
		}
		if (!image.isMutable()) partialAlphaImageCache.put(image,Boolean.valueOf(partial));
		return partial;
	}

	private static boolean isNonOpaqueAlpha(int alpha)
	{
		// Keep cutout textures in the deferred alpha pass, as in the game
		// renderer's established ordering. The rasterizer still writes depth
		// for fully opaque texels, but not for cutouts or blended fragments.
		return alpha<255;
	}

	private void renderTranslucent(List<DeferredDraw> translucent)
	{
		if (translucent.isEmpty()) return;
		renderer.setDepthWriteSuppressed(true);
		try {
			for (DeferredDraw call:translucent) renderDeferred(call);
		} finally {
			renderer.setDepthWriteSuppressed(false);
		}
	}

	private void renderAdditive(List<DeferredDraw> additive)
	{
		for (DeferredDraw call:additive) renderDeferred(call);
	}

	private void renderDeferred(DeferredDraw call)
	{
		if (call.submesh<0) renderer.render((Sprite3D)call.node,call.transform);
		else renderSubmesh((Mesh)call.node,call.submesh,call.transform);
	}

	private void renderSubmesh(Mesh mesh,int submesh,Transform transform)
	{
		if (mesh instanceof SkinnedMesh)
			renderer.render((SkinnedMesh)mesh,mesh.getIndexBuffer(submesh),mesh.getAppearance(submesh),transform);
		else if (mesh instanceof MorphingMesh)
			renderer.render((MorphingMesh)mesh,mesh.getIndexBuffer(submesh),mesh.getAppearance(submesh),transform);
		else
			renderer.render(mesh.getVertexBuffer(),mesh.getIndexBuffer(submesh),mesh.getAppearance(submesh),transform);
	}

	private static final class DeferredDraw
	{
		final Node node;
		final Transform transform;
		final int submesh;

		DeferredDraw(Node node,Transform transform,int submesh)
		{
			this.node=node;
			this.transform=new Transform(transform);
			this.submesh=submesh;
		}
	}

	private static final class DeferredDirectDraw
	{
		final VertexBuffer vertices;
		final IndexBuffer triangles;
		final Appearance appearance;
		final Transform transform;
		final Camera camera;
		final Transform cameraTransform;
		final int viewportX,viewportY,viewportWidth,viewportHeight;
		final List<M3GSoftwareRenderer.LightSource> lights;

		DeferredDirectDraw(VertexBuffer vertices,IndexBuffer triangles,Appearance appearance,Transform transform,
				Camera camera,Transform cameraTransform,int viewportX,int viewportY,int viewportWidth,int viewportHeight,
				List<M3GSoftwareRenderer.LightSource> lights)
		{
			this.vertices=vertices;
			this.triangles=triangles;
			this.appearance=appearance;
			this.transform=transform==null?new Transform():new Transform(transform);
			this.camera=camera;
			this.cameraTransform=new Transform(cameraTransform);
			this.viewportX=viewportX;
			this.viewportY=viewportY;
			this.viewportWidth=viewportWidth;
			this.viewportHeight=viewportHeight;
			this.lights=new ArrayList<M3GSoftwareRenderer.LightSource>(lights);
		}
	}

	private List<M3GSoftwareRenderer.LightSource> contextLightSources()
	{
		List<M3GSoftwareRenderer.LightSource> result=new ArrayList<M3GSoftwareRenderer.LightSource>();
		for(int i=0;i<lights.size();i++) {
			Light light=lights.get(i);
			if(light!=null) result.add(new M3GSoftwareRenderer.LightSource(light,lightTransforms.get(i)));
		}
		return result;
	}

	private void collectLights(Node node,Transform parent,boolean applyLocalTransform,
			List<M3GSoftwareRenderer.LightSource> result)
	{
		if(node==null||!node.isRenderingEnabled()) return;
		Transform world=new Transform(parent);
		if(applyLocalTransform) {
			Transform local=new Transform();
			node.getLocalCompositeTransform(local);
			world.postMultiply(local);
		}
		if(node instanceof Light) result.add(new M3GSoftwareRenderer.LightSource((Light)node,world));
		if(node instanceof Group) {
			for(Node child:((Group)node).children()) collectLights(child,world,true,result);
		}
	}

}

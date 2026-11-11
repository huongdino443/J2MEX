/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
*/
package javax.microedition.m3g;

import java.util.Arrays;
import java.util.IdentityHashMap;
import javax.microedition.lcdui.Graphics;

/** Small framebuffer renderer for the M3G feature set used by the Android MVP. */
final class M3GSoftwareRenderer
{
	private Graphics target;
	private int viewportX, viewportY, width, height;
	private int[] color;
	private float[] depth;
	private Vertex[] vertexCache = new Vertex[0];
	private int[] indexCache = new int[0];
private final Vertex[] clipBufferA = createVertexBuffer(12);
private final Vertex[] clipBufferB = createVertexBuffer(12);
	private final Vertex spriteCenter = new Vertex();
	private final float[] spriteModelMatrix = new float[16];
	private final float[] spriteViewMatrix = new float[16];
	private final Transform spriteViewTransform = new Transform();
	private final Transform spriteProjectionTransform = new Transform();
	private boolean depthBufferEnabled;
private boolean contentsInitialized;
private int targetOriginX,targetOriginY;
	private Camera camera;
	private Transform cameraWorld = new Transform();
	private final IdentityHashMap<SkinnedMesh, Long> theFightSkinnedTraceTimes =
			new IdentityHashMap<SkinnedMesh, Long>();
	private boolean theFightSkinnedTraceChecked;
	private boolean theFightSkinnedTraceEnabled;
	private int theFightSkinnedTraceCount;

	void bind(Object target, boolean depthBuffer)
	{
		if (!(target instanceof Graphics))
			throw new IllegalArgumentException("M3G target must be an LCDUI Graphics instance");
		this.target = (Graphics) target;
		this.depthBufferEnabled = depthBuffer;
			viewportX = this.target.getClipX();
			viewportY = this.target.getClipY();
			width = Math.max(1, this.target.getClipWidth());
			height = Math.max(1, this.target.getClipHeight());
targetOriginX=this.target.getTranslateX();
targetOriginY=this.target.getTranslateY();
		ensureBuffers();
contentsInitialized=false;
// Depth storage belongs to this binding, not a previous target or frame.
Arrays.fill(depth,0f);
	}

	void setViewport(int x, int y, int width, int height)
	{
		if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid M3G viewport");
if(this.viewportX==x&&this.viewportY==y&&this.width==width&&this.height==height) return;
if(target!=null&&contentsInitialized) publish();
		this.viewportX=x; this.viewportY=y; this.width=width; this.height=height;
		ensureBuffers();
contentsInitialized=false;
	}

	void setCamera(Camera camera, Transform transform)
	{
		this.camera = camera;
		if (transform != null) cameraWorld.set(transform);
		else if (camera != null) camera.getCompositeTransform(cameraWorld);
	}

	Camera camera(Transform transform)
	{
		if (transform != null) transform.set(cameraWorld);
		return camera;
	}

	boolean hasDepthBuffer() { return depthBufferEnabled; }

	void clear(Background background)
	{
		if (target == null) throw new IllegalStateException("No M3G target is bound");
		ensureBuffers();
		if (background == null || background.isColorClearEnabled()) {
int clearColor = background == null ? 0xff000000 : 0xff000000|background.getColor();
			Arrays.fill(color, clearColor);
contentsInitialized=true;
			if (background != null && background.getImage() != null)
				drawBackground(background);
} else importTargetContents();
		if (depthBufferEnabled && (background == null || background.isDepthClearEnabled()))
			Arrays.fill(depth, 0f);
	}

	void release()
	{
		if (target == null) throw new IllegalStateException("No M3G target is bound");
importTargetContents();
publish();
		target = null;
contentsInitialized=false;
	}

private void publish()
{
// A MIDP Graphics target is RGB. Blending already happened against its
// imported contents; do not apply source-over a second time during writeback.
boolean rgb=target instanceof org.recompile.mobile.PlatformGraphics;
target.drawRGB(color,0,width,viewportX,viewportY,width,height,!rgb);
}

private void importTargetContents()
{
if(contentsInitialized) return;
Arrays.fill(color,0);
if(target instanceof org.recompile.mobile.PlatformGraphics) {
java.awt.image.BufferedImage image=((org.recompile.mobile.PlatformGraphics)target).getCanvas();
int x=viewportX+targetOriginX,y=viewportY+targetOriginY;
int left=Math.max(0,x),top=Math.max(0,y);
int right=(int)Math.min((long)image.getWidth(),(long)x+width);
int bottom=(int)Math.min((long)image.getHeight(),(long)y+height);
if(right>left&&bottom>top)
image.getRGB(left,top,right-left,bottom-top,color,(top-y)*width+left-x,width);
}
// Write-only test Graphics receive a fresh overlay. Real MIDP Graphics
// always use the snapshot above, including 2D drawing done before binding.
contentsInitialized=true;
}

	void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform model)
{
render(vertices, triangles, appearance, model, null, null);
}

void render(MorphingMesh mesh, IndexBuffer triangles, Appearance appearance, Transform model)
{
if (mesh == null) throw new NullPointerException("mesh");
render(mesh.getVertexBuffer(), triangles, appearance, model, mesh, null);
}

void render(SkinnedMesh mesh, IndexBuffer triangles, Appearance appearance, Transform model)
{
if (mesh == null) throw new NullPointerException("mesh");
render(mesh.getVertexBuffer(), triangles, appearance, model, null, mesh);
}

private void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform model,
MorphingMesh morph, SkinnedMesh skinned)
	{
		if (target == null) throw new IllegalStateException("No M3G target is bound");
		if (vertices == null || triangles == null) throw new NullPointerException();
		if (vertices.getVertexCount() == 0 || triangles.getIndexCount() < 3) return;
		ensureBuffers();
importTargetContents();

		int vertexCount = vertices.getVertexCount();
		ensureVertexCache(vertexCount);
		Vertex[] transformed = vertexCache;
		float[] skinnedPositions = skinned == null ? null : skinned.positionsForRendering();
		boolean traceSkinned = skinned != null && shouldTraceTheFightSkinnedMesh(skinned);
		float[] modelMatrix = new float[16];
		float[] viewMatrix = new float[16];
if (model == null) identity(modelMatrix); else model.getColumnMajor(modelMatrix);
		Transform view = new Transform(cameraWorld);
		try { view.invert(); }
		catch (ArithmeticException singular) { view.setIdentity(); }
view.getColumnMajor(viewMatrix);

Camera activeCamera=camera;
if(activeCamera==null) { activeCamera=new Camera(); activeCamera.setPerspective(45,width/(float)height,0.1f,10000); }
		Transform projectionTransform=new Transform();
activeCamera.getProjection(projectionTransform);
float[] projection=projectionTransform.values();
		float near = camera == null ? 0.1f : camera.nearPlane();
		float far = camera == null ? 10000f : camera.farPlane();
		if (far <= near) far = near + 1f;
		float[] scaleBias = new float[4];
		VertexArray positions = vertices.getPositions(scaleBias);
		if (positions == null) return;
		VertexArray colors = vertices.getColors();
		VertexArray textureCoords = vertices.getTexCoords(0, scaleBias);
		Texture2D texture = appearance == null ? null : appearance.getTexture(0);
		int defaultColor = vertices.getDefaultColor();
Material material=appearance==null?null:appearance.getMaterial();
if(colors==null || colors.getComponentCount()<3) {
	if(material!=null) defaultColor=material.getColor(Material.DIFFUSE);
	else if(texture!=null) {
		// A textured mesh without a material is unlit. Use an opaque white
		// modulation color instead of the serialized material-path alpha.
		defaultColor=0xffffffff;
	}
}

		float traceMinX=Float.POSITIVE_INFINITY, traceMinY=Float.POSITIVE_INFINITY,
				traceMinZ=Float.POSITIVE_INFINITY, traceMaxX=Float.NEGATIVE_INFINITY,
				traceMaxY=Float.NEGATIVE_INFINITY, traceMaxZ=Float.NEGATIVE_INFINITY;
		float traceScreenMinX=Float.POSITIVE_INFINITY, traceScreenMinY=Float.POSITIVE_INFINITY,
				traceScreenMaxX=Float.NEGATIVE_INFINITY, traceScreenMaxY=Float.NEGATIVE_INFINITY;
		int traceVisibleVertices=0;
		for (int i = 0; i < vertexCount; i++) {
float x=morph!=null?morph.positionForRendering(i,0):skinnedPositions!=null?skinnedPositions[i*3]:vertices.position(i,0);
float y=morph!=null?morph.positionForRendering(i,1):skinnedPositions!=null?skinnedPositions[i*3+1]:vertices.position(i,1);
float z=morph!=null?morph.positionForRendering(i,2):skinnedPositions!=null?skinnedPositions[i*3+2]:vertices.position(i,2);
			if (traceSkinned) {
				traceMinX=Math.min(traceMinX,x); traceMinY=Math.min(traceMinY,y); traceMinZ=Math.min(traceMinZ,z);
				traceMaxX=Math.max(traceMaxX,x); traceMaxY=Math.max(traceMaxY,y); traceMaxZ=Math.max(traceMaxZ,z);
			}
			float wx=modelMatrix[0]*x+modelMatrix[4]*y+modelMatrix[8]*z+modelMatrix[12];
			float wy=modelMatrix[1]*x+modelMatrix[5]*y+modelMatrix[9]*z+modelMatrix[13];
			float wz=modelMatrix[2]*x+modelMatrix[6]*y+modelMatrix[10]*z+modelMatrix[14];
			float ww=modelMatrix[3]*x+modelMatrix[7]*y+modelMatrix[11]*z+modelMatrix[15];
			if (ww != 0 && ww != 1) { wx/=ww; wy/=ww; wz/=ww; }
			float cx=viewMatrix[0]*wx+viewMatrix[4]*wy+viewMatrix[8]*wz+viewMatrix[12];
			float cy=viewMatrix[1]*wx+viewMatrix[5]*wy+viewMatrix[9]*wz+viewMatrix[13];
			float cz=viewMatrix[2]*wx+viewMatrix[6]*wy+viewMatrix[10]*wz+viewMatrix[14];
			float u=textureCoords == null ? 0 : vertices.texCoord(0,i,0);
			float v=textureCoords == null || textureCoords.getComponentCount()<2 ? 0 : vertices.texCoord(0,i,1);
			setVertexColor(transformed[i], vertices, i, colors, defaultColor);
if (!project(transformed[i],cx,cy,cz,u,v,projection))
				transformed[i].valid=false;
			if (traceSkinned && transformed[i].valid) {
				traceVisibleVertices++;
				traceScreenMinX=Math.min(traceScreenMinX,transformed[i].x);
				traceScreenMinY=Math.min(traceScreenMinY,transformed[i].y);
				traceScreenMaxX=Math.max(traceScreenMaxX,transformed[i].x);
				traceScreenMaxY=Math.max(traceScreenMaxY,transformed[i].y);
			}
		}
		if (traceSkinned) traceTheFightSkinnedMesh(skinned,modelMatrix,vertexCount,
				traceMinX,traceMinY,traceMinZ,traceMaxX,traceMaxY,traceMaxZ,
				traceVisibleVertices,traceScreenMinX,traceScreenMinY,traceScreenMaxX,traceScreenMaxY);

		if (indexCache.length < triangles.getIndexCount()) indexCache = new int[triangles.getIndexCount()];
		int[] indices = indexCache;
		triangles.getIndices(indices);
		int[] strips = triangles instanceof TriangleStripArray
				? ((TriangleStripArray)triangles).stripLengths() : new int[]{indices.length};
		PolygonMode polygon = appearance == null ? null : appearance.getPolygonMode();
		CompositingMode compositing = appearance == null ? null : appearance.getCompositingMode();
		Fog fog = appearance == null ? null : appearance.getFog();
		TextureData tex = texture == null ? null : new TextureData(texture);

		int offset=0;
		for (int stripLength : strips) {
			for (int j=0;j+2<stripLength;j++) {
				int i0=indices[offset+j], i1=indices[offset+j+1], i2=indices[offset+j+2];
				if ((j&1)!=0) { int swap=i0; i0=i1; i1=swap; }
				if (i0<0||i1<0||i2<0||i0>=vertexCount||i1>=vertexCount||i2>=vertexCount) continue;
				Vertex a=transformed[i0], b=transformed[i1], c=transformed[i2];
if (a.valid&&b.valid&&c.valid)
rasterize(a,b,c,polygon,compositing,fog,tex,defaultColor,near,far);
else
rasterizeClipped(a,b,c,polygon,compositing,fog,tex,defaultColor,near,far);
			}
			offset += stripLength;
		}
	}

	private synchronized boolean shouldTraceTheFightSkinnedMesh(SkinnedMesh mesh)
	{
		if (!theFightSkinnedTraceChecked) {
			theFightSkinnedTraceChecked=true;
			org.recompile.mobile.MobilePlatform platform=org.recompile.mobile.Mobile.getPlatform();
			String name=platform==null||platform.loader==null?null:platform.loader.name;
			theFightSkinnedTraceEnabled=name!=null
					&& name.toLowerCase(java.util.Locale.ROOT).contains("thefight3d");
		}
		if (!theFightSkinnedTraceEnabled||theFightSkinnedTraceCount>=24) return false;
		long now=System.currentTimeMillis();
		Long previous=theFightSkinnedTraceTimes.get(mesh);
		if (previous!=null&&now-previous.longValue()<5000L) return false;
		theFightSkinnedTraceTimes.put(mesh,Long.valueOf(now));
		theFightSkinnedTraceCount++;
		return true;
	}

	private void traceTheFightSkinnedMesh(SkinnedMesh mesh,float[] modelMatrix,int vertexCount,
			float minX,float minY,float minZ,float maxX,float maxY,float maxZ,
			int visibleVertices,float screenMinX,float screenMinY,float screenMaxX,float screenMaxY)
	{
		try {
			Transform local=new Transform(),composite=new Transform(),skeleton=new Transform();
			mesh.getLocalCompositeTransform(local);
			mesh.getCompositeTransform(composite);
			mesh.getSkeleton().getCompositeTransform(skeleton);
			float[] localMatrix=new float[16],compositeMatrix=new float[16],skeletonMatrix=new float[16];
			local.getColumnMajor(localMatrix);
			composite.getColumnMajor(compositeMatrix);
			skeleton.getColumnMajor(skeletonMatrix);
			float[] cameraMatrix=new float[16];
			cameraWorld.getColumnMajor(cameraMatrix);
			System.out.println("[TheFight3D-model] sample="+theFightSkinnedTraceCount
					+" mesh="+Integer.toHexString(System.identityHashCode(mesh))
					+" uid="+mesh.getUserID()+" vertices="+vertexCount
					+" skinnedBounds="+Arrays.toString(new float[]{minX,minY,minZ,maxX,maxY,maxZ})
					+" visibleVertices="+visibleVertices
					+" screenBounds="+Arrays.toString(new float[]{screenMinX,screenMinY,screenMaxX,screenMaxY})
					+" renderModel="+Arrays.toString(modelMatrix)
					+" meshLocal="+Arrays.toString(localMatrix)
					+" meshHierarchy="+Arrays.toString(compositeMatrix)
					+" skeletonRoot="+Arrays.toString(skeletonMatrix)
					+" cameraWorld="+Arrays.toString(cameraMatrix));
		} catch (Throwable traceFailure) {
			System.out.println("[TheFight3D-model] trace failed: "+traceFailure);
		}
	}

	void render(Sprite3D sprite, Transform model)
	{
		if (target == null) throw new IllegalStateException("No M3G target is bound");
		if (sprite == null) throw new NullPointerException("sprite");
		Image2D image=sprite.getImage();
		if (image==null) return;
		Appearance appearance=sprite.getAppearance();
		if (appearance==null) return;
		int imageWidth=image.getWidth(),imageHeight=image.getHeight();
		int cropX=sprite.getCropX(),cropY=sprite.getCropY();
		int cropWidth=sprite.getCropWidth()>0?sprite.getCropWidth():imageWidth;
		int cropHeight=sprite.getCropHeight()>0?sprite.getCropHeight():imageHeight;
		if(cropWidth<=0||cropHeight<=0) return;
		int visibleX=Math.max(0,cropX),visibleY=Math.max(0,cropY);
		int visibleRight=(int)Math.min((long)imageWidth,(long)cropX+cropWidth);
		int visibleBottom=(int)Math.min((long)imageHeight,(long)cropY+cropHeight);
		int visibleWidth=visibleRight-visibleX,visibleHeight=visibleBottom-visibleY;
		if(visibleWidth<=0||visibleHeight<=0) return;

		ensureBuffers();
		importTargetContents();
		if(model==null) identity(spriteModelMatrix); else model.getColumnMajor(spriteModelMatrix);
		spriteViewTransform.set(cameraWorld);
		try { spriteViewTransform.invert(); }
		catch (ArithmeticException singular) { spriteViewTransform.setIdentity(); }
		spriteViewTransform.getColumnMajor(spriteViewMatrix);

		Camera activeCamera=camera;
		if(activeCamera==null) {
			activeCamera=new Camera();
			activeCamera.setPerspective(45,width/(float)height,0.1f,10000);
		}
		activeCamera.getProjection(spriteProjectionTransform);
		float[] projection=spriteProjectionTransform.values();
		float near=camera==null?0.1f:camera.nearPlane();
		float far=camera==null?10000f:camera.farPlane();
		if(far<=near) far=near+1f;

		float centerX=spriteModelMatrix[12],centerY=spriteModelMatrix[13],centerZ=spriteModelMatrix[14];
		if(!projectSpritePoint(spriteCenter,centerX,centerY,centerZ,0.5f,0.5f,projection)) return;
		float halfWidth=cropWidth*0.5f,halfHeight=cropHeight*0.5f;
		if(sprite.isScaled()) {
			float scaleX=(float)Math.sqrt(spriteModelMatrix[0]*spriteModelMatrix[0]
					+spriteModelMatrix[1]*spriteModelMatrix[1]+spriteModelMatrix[2]*spriteModelMatrix[2]);
			float scaleY=(float)Math.sqrt(spriteModelMatrix[4]*spriteModelMatrix[4]
					+spriteModelMatrix[5]*spriteModelMatrix[5]+spriteModelMatrix[6]*spriteModelMatrix[6]);
			if(scaleX==0||scaleY==0) return;
			// M3G scaled sprites occupy a one-unit billboard, independent of
			// the pixel dimensions of the selected crop rectangle.
			halfWidth=0.5f*scaleX*Math.abs(projection[0]*spriteCenter.invZ*width*0.5f);
			halfHeight=0.5f*scaleY*Math.abs(projection[5]*spriteCenter.invZ*height*0.5f);
		}
		if(!(halfWidth>0)||!(halfHeight>0)||Float.isInfinite(halfWidth)||Float.isInfinite(halfHeight)) return;
		float cropOffsetX=2f*cropX+cropWidth-2f*visibleX-visibleWidth;
		float cropOffsetY=2f*cropY+cropHeight-2f*visibleY-visibleHeight;
		float visibleCenterX=spriteCenter.x-cropOffsetX*halfWidth/cropWidth;
		float visibleCenterY=spriteCenter.y-cropOffsetY*halfHeight/cropHeight;
		halfWidth*=visibleWidth/(float)cropWidth;
		halfHeight*=visibleHeight/(float)cropHeight;
		CompositingMode compositing=appearance==null?null:appearance.getCompositingMode();
		Fog fog=appearance==null?null:appearance.getFog();
		TextureData texture=new TextureData(image);
		int minX=Math.max(viewportX,(int)Math.ceil(visibleCenterX-halfWidth-0.5f));
		int maxX=Math.min(viewportX+width-1,(int)Math.floor(visibleCenterX+halfWidth-0.5f));
		int minY=Math.max(viewportY,(int)Math.ceil(visibleCenterY-halfHeight-0.5f));
		int maxY=Math.min(viewportY+height-1,(int)Math.floor(visibleCenterY+halfHeight-0.5f));
		if(minX>maxX||minY>maxY) return;
		boolean depthTest=depthBufferEnabled&&(compositing==null||compositing.isDepthTestEnabled());
		boolean depthWrite=depthBufferEnabled&&(compositing==null||compositing.isDepthWriteEnabled());
		boolean colorWrite=compositing==null||compositing.isColorWriteEnabled();
		float threshold=compositing==null?0:compositing.getAlphaThreshold();
		for(int py=minY;py<=maxY;py++) {
			float v=(visibleY+((py+0.5f-(visibleCenterY-halfHeight))/(2f*halfHeight))*visibleHeight)/imageHeight;
			for(int px=minX;px<=maxX;px++) {
				float u=(visibleX+((px+0.5f-(visibleCenterX-halfWidth))/(2f*halfWidth))*visibleWidth)/imageWidth;
				int pos=(py-viewportY)*width+(px-viewportX);
				if(depthTest&&spriteCenter.depth<depth[pos]) continue;
				int src=shade(u,v,1f,1f,1f,1f,texture,fog,spriteCenter.distance,near,far);
				if(((src>>>24)&255)/255f<threshold) continue;
				if(depthWrite) depth[pos]=spriteCenter.depth;
				if(colorWrite) color[pos]=blend(src,color[pos],compositing);
			}
		}
	}

	private boolean projectSpritePoint(Vertex out,float x,float y,float z,float u,float v,float[] projection)
	{
		float cx=spriteViewMatrix[0]*x+spriteViewMatrix[4]*y+spriteViewMatrix[8]*z+spriteViewMatrix[12];
		float cy=spriteViewMatrix[1]*x+spriteViewMatrix[5]*y+spriteViewMatrix[9]*z+spriteViewMatrix[13];
		float cz=spriteViewMatrix[2]*x+spriteViewMatrix[6]*y+spriteViewMatrix[10]*z+spriteViewMatrix[14];
		return project(out,cx,cy,cz,u,v,projection);
	}

private boolean project(Vertex out,float x,float y,float z,float u,float v,float[] m)
	{
float cx=m[0]*x+m[4]*y+m[8]*z+m[12];
float cy=m[1]*x+m[5]*y+m[9]*z+m[13];
float cz=m[2]*x+m[6]*y+m[10]*z+m[14];
float cw=m[3]*x+m[7]*y+m[11]*z+m[15];
out.clipX=cx; out.clipY=cy; out.clipZ=cz; out.clipW=cw;
out.distance=-z; out.u=u; out.v=v;
out.valid=insideClipVolume(out);
return out.valid&&projectToScreen(out);
}

private boolean insideClipVolume(Vertex vertex)
{
return vertex.clipW>0.000001f
&&vertex.clipX+vertex.clipW>=0&&vertex.clipW-vertex.clipX>=0
&&vertex.clipY+vertex.clipW>=0&&vertex.clipW-vertex.clipY>=0
&&vertex.clipZ+vertex.clipW>=0&&vertex.clipW-vertex.clipZ>=0;
}

private boolean projectToScreen(Vertex vertex)
{
float cw=vertex.clipW;
if (!(cw>0.000001f)) return false;
float nx=vertex.clipX/cw,ny=vertex.clipY/cw;
vertex.x=viewportX+(nx+1f)*width*0.5f;
vertex.y=viewportY+(1f-ny)*height*0.5f;
vertex.invZ=1f/cw;
vertex.depth=(1f-vertex.clipZ/cw)*0.5f;
vertex.valid=true;
return true;
}

private void rasterizeClipped(Vertex a,Vertex b,Vertex c,PolygonMode polygon,
CompositingMode mode,Fog fog,TextureData texture,int defaultColor,float near,float far)
{
Vertex[] input=clipBufferA;
Vertex[] output=clipBufferB;
input[0].copyFrom(a); input[1].copyFrom(b); input[2].copyFrom(c);
int count=3;
for (int plane=0;plane<6;plane++) {
int outputCount=0;
Vertex previous=input[count-1];
float previousDistance=clipDistance(previous,plane);
boolean previousInside=previousDistance>=0;
for (int i=0;i<count;i++) {
Vertex current=input[i];
float currentDistance=clipDistance(current,plane);
boolean currentInside=currentDistance>=0;
if (previousInside!=currentInside) {
float denominator=previousDistance-currentDistance;
if (denominator!=0&&outputCount<output.length)
output[outputCount++].interpolate(previous,current,previousDistance/denominator);
}
if (currentInside&&outputCount<output.length)
output[outputCount++].copyFrom(current);
previous=current;
previousDistance=currentDistance;
previousInside=currentInside;
}
if (outputCount<3) return;
Vertex[] swap=input; input=output; output=swap;
count=outputCount;
}
if (!projectToScreen(input[0])) return;
for (int i=1;i+1<count;i++) {
if (!projectToScreen(input[i])||!projectToScreen(input[i+1])) continue;
rasterize(input[0],input[i],input[i+1],polygon,mode,fog,texture,
defaultColor,near,far);
}
}

private static float clipDistance(Vertex vertex,int plane)
{
switch (plane) {
case 0: return vertex.clipX+vertex.clipW;
case 1: return vertex.clipW-vertex.clipX;
case 2: return vertex.clipY+vertex.clipW;
case 3: return vertex.clipW-vertex.clipY;
case 4: return vertex.clipZ+vertex.clipW;
default: return vertex.clipW-vertex.clipZ;
}
}

private static Vertex[] createVertexBuffer(int count)
{
Vertex[] vertices=new Vertex[count];
for (int i=0;i<count;i++) vertices[i]=new Vertex();
return vertices;
	}

	private void rasterize(Vertex a,Vertex b,Vertex c,PolygonMode polygon,CompositingMode mode,
			Fog fog,TextureData texture,int defaultColor,float near,float far)
	{
		float area=edge(a.x,a.y,b.x,b.y,c.x,c.y);
		if (Math.abs(area)<0.0001f) return;
		boolean front=(polygon==null||polygon.getWinding()==PolygonMode.WINDING_CCW)?area>0:area<0;
		if (polygon!=null && ((polygon.getCulling()==PolygonMode.CULL_BACK&&!front)
				||(polygon.getCulling()==PolygonMode.CULL_FRONT&&front))) return;
		int minX=Math.max(viewportX,(int)Math.floor(Math.min(a.x,Math.min(b.x,c.x))));
		int maxX=Math.min(viewportX+width-1,(int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))));
		int minY=Math.max(viewportY,(int)Math.floor(Math.min(a.y,Math.min(b.y,c.y))));
		int maxY=Math.min(viewportY+height-1,(int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))));
		if (minX>maxX||minY>maxY) return;
		float invArea=1f/area;
		boolean perspective=polygon==null||polygon.isPerspectiveCorrectionEnabled();
		boolean depthTest=depthBufferEnabled&&(mode==null||mode.isDepthTestEnabled());
		boolean depthWrite=depthBufferEnabled&&(mode==null||mode.isDepthWriteEnabled());
		boolean colorWrite=mode==null||mode.isColorWriteEnabled();
		float threshold=mode==null?0:mode.getAlphaThreshold();
		for (int py=minY;py<=maxY;py++) {
			float y=py+0.5f;
			for (int px=minX;px<=maxX;px++) {
				float x=px+0.5f;
				float wa=edge(b.x,b.y,c.x,c.y,x,y)*invArea;
				float wb=edge(c.x,c.y,a.x,a.y,x,y)*invArea;
				float wc=1f-wa-wb;
				if (wa<0||wb<0||wc<0) continue;
				float invZ=wa*a.invZ+wb*b.invZ+wc*c.invZ;
				if (invZ<=0) continue;
				int pos=(py-viewportY)*width+(px-viewportX);
float fragmentDepth=wa*a.depth+wb*b.depth+wc*c.depth;
if (depthTest&&fragmentDepth<depth[pos]) continue;
				float u,v,red,green,blue,alpha;
				if (perspective) {
					u=(wa*a.u*a.invZ+wb*b.u*b.invZ+wc*c.u*c.invZ)/invZ;
					v=(wa*a.v*a.invZ+wb*b.v*b.invZ+wc*c.v*c.invZ)/invZ;
					red=(wa*a.r*a.invZ+wb*b.r*b.invZ+wc*c.r*c.invZ)/invZ;
					green=(wa*a.g*a.invZ+wb*b.g*b.invZ+wc*c.g*c.invZ)/invZ;
					blue=(wa*a.b*a.invZ+wb*b.b*b.invZ+wc*c.b*c.invZ)/invZ;
					alpha=(wa*a.a*a.invZ+wb*b.a*b.invZ+wc*c.a*c.invZ)/invZ;
				} else {
					u=wa*a.u+wb*b.u+wc*c.u; v=wa*a.v+wb*b.v+wc*c.v;
					red=wa*a.r+wb*b.r+wc*c.r; green=wa*a.g+wb*b.g+wc*c.g;
					blue=wa*a.b+wb*b.b+wc*c.b; alpha=wa*a.a+wb*b.a+wc*c.a;
				}
float distance=(wa*a.distance*a.invZ+wb*b.distance*b.invZ+wc*c.distance*c.invZ)/invZ;
				int src=shade(u,v,red,green,blue,alpha,texture,fog,distance,near,far);
				int sourceAlpha=(src>>>24)&255;
				if (sourceAlpha/255f<threshold) continue;
if (depthWrite) depth[pos]=fragmentDepth;
				if (colorWrite) color[pos]=blend(src,color[pos],mode);
			}
		}
	}

	private int shade(float u,float v,float r,float g,float b,float a,TextureData tex,
			Fog fog,float distance,float near,float far)
	{
		int rr=channel(r), gg=channel(g), bb=channel(b), aa=channel(a);
		if (tex!=null && tex.pixels!=null && tex.width>0 && tex.height>0) {
			int sample=tex.sample(u,v);
			int tr=(sample>>>16)&255,tg=(sample>>>8)&255,tb=sample&255,ta=(sample>>>24)&255;
			switch(tex.blending) {
			case Texture2D.FUNC_REPLACE: rr=tr; gg=tg; bb=tb; aa=ta; break;
			case Texture2D.FUNC_DECAL:
				float decal=ta/255f; rr=mix(rr,tr,decal); gg=mix(gg,tg,decal); bb=mix(bb,tb,decal); break;
			case Texture2D.FUNC_BLEND:
				rr=mix(tex.blendR,tr,rr/255f); gg=mix(tex.blendG,tg,gg/255f); bb=mix(tex.blendB,tb,bb/255f); aa=aa*ta/255; break;
			default: rr=rr*tr/255; gg=gg*tg/255; bb=bb*tb/255; aa=aa*ta/255; break;
			}
		}
		if (fog!=null) {
			float fogAmount;
			if (fog.getMode()==Fog.LINEAR) {
				float start=fog.getNearDistance(), end=fog.getFarDistance();
				fogAmount=end<=start?(distance>=end?1f:0f):clamp((distance-start)/(end-start));
			} else if (fog.getMode()==Fog.EXPONENTIAL) {
				fogAmount=1f-(float)Math.exp(-fog.getDensity()*distance);
			} else fogAmount=0;
			int fc=fog.getColor();
			rr=mix(rr,(fc>>>16)&255,fogAmount);
			gg=mix(gg,(fc>>>8)&255,fogAmount);
			bb=mix(bb,fc&255,fogAmount);
		}
		return (clampByte(aa)<<24)|(clampByte(rr)<<16)|(clampByte(gg)<<8)|clampByte(bb);
	}

	private int blend(int src,int dst,CompositingMode mode)
	{
		if (mode==null||mode.getBlending()==CompositingMode.ALPHA) return alphaBlend(src,dst);
		if (mode.getBlending()==CompositingMode.REPLACE) return src;
		int sa=(src>>>24)&255,sr=(src>>>16)&255,sg=(src>>>8)&255,sb=src&255;
		int da=(dst>>>24)&255,dr=(dst>>>16)&255,dg=(dst>>>8)&255,db=dst&255;
		if (mode.getBlending()==CompositingMode.ALPHA_ADD)
			return (Math.min(255,da+sa)<<24)|(Math.min(255,dr+sr*sa/255)<<16)
					|(Math.min(255,dg+sg*sa/255)<<8)|Math.min(255,db+sb*sa/255);
		if (mode.getBlending()==CompositingMode.MODULATE)
			return (sa*da/255<<24)|(sr*dr/255<<16)|(sg*dg/255<<8)|sb*db/255;
		if (mode.getBlending()==CompositingMode.MODULATE_X2)
			return (sa*da/255<<24)|(Math.min(255,sr*dr/128)<<16)
					|(Math.min(255,sg*dg/128)<<8)|Math.min(255,sb*db/128);
		return src;
	}

	private void drawBackground(Background background)
	{
		Image2D image=background.getImage();
		int[] src=image.pixels();
		if (src==null||image.getWidth()<=0||image.getHeight()<=0) return;
		int cropW=background.getCropWidth()>0?background.getCropWidth():image.getWidth();
		int cropH=background.getCropHeight()>0?background.getCropHeight():image.getHeight();
		int cropX=background.getCropX(),cropY=background.getCropY();
		for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
			int sx=cropX+x*cropW/width, sy=cropY+y*cropH/height;
			sx=wrap(sx,image.getWidth(),background.getImageModeX()==Background.REPEAT);
			sy=wrap(sy,image.getHeight(),background.getImageModeY()==Background.REPEAT);
			color[y*width+x]=src[sy*image.getWidth()+sx];
		}
	}

	private void ensureBuffers()
	{
		if (width<=0||height<=0) return;
		long size=(long)width*height;
		if (size>4_000_000L) throw new IllegalArgumentException("M3G viewport is too large");
		if (color==null||color.length!=(int)size) {
			color=new int[(int)size]; depth=new float[(int)size];
			Arrays.fill(color,0); Arrays.fill(depth,0f);
		}
	}

	private static void setVertexColor(Vertex out,VertexBuffer vb,int index,VertexArray colors,int defaultColor)
	{
		if (colors==null||colors.getComponentCount()<3) {
			out.r=((defaultColor>>>16)&255)/255f; out.g=((defaultColor>>>8)&255)/255f;
			out.b=(defaultColor&255)/255f; out.a=((defaultColor>>>24)&255)/255f;
			return;
		}
		float scale=colors.getComponentType()==VertexArray.SHORT?65535f:255f;
		float r=colors.unsignedComponent(index,0)/scale,g=colors.unsignedComponent(index,1)/scale;
		float b=colors.unsignedComponent(index,2)/scale;
		float a=colors.getComponentCount()>3?colors.unsignedComponent(index,3)/scale:1f;
		out.r=r; out.g=g; out.b=b; out.a=a;
	}

	private void ensureVertexCache(int count)
	{
		if (vertexCache.length<count) {
			Vertex[] bigger=new Vertex[count];
			System.arraycopy(vertexCache,0,bigger,0,vertexCache.length);
			for (int i=vertexCache.length;i<count;i++) bigger[i]=new Vertex();
			vertexCache=bigger;
		}
	}

	private static int alphaBlend(int src,int dst)
	{
		int a=(src>>>24)&255, inv=255-a;
		int r=(((src>>>16)&255)*a+((dst>>>16)&255)*inv)/255;
		int g=(((src>>>8)&255)*a+((dst>>>8)&255)*inv)/255;
		int b=((src&255)*a+(dst&255)*inv)/255;
		int da=(dst>>>24)&255;
		return (Math.min(255,a+da*inv/255)<<24)|(r<<16)|(g<<8)|b;
	}

	private static float edge(float ax,float ay,float bx,float by,float px,float py)
	{
		return (px-ax)*(by-ay)-(py-ay)*(bx-ax);
	}

	private static void identity(float[] m) { m[0]=m[5]=m[10]=m[15]=1; }
	private static int channel(float value) { return clampByte(Math.round(value*255f)); }
	private static int clampByte(int v) { return v<0?0:v>255?255:v; }
	private static float clamp(float v) { return v<0?0:v>1?1:v; }
	private static int mix(int a,int b,float t) { return clampByte(Math.round(a+(b-a)*t)); }
	private static int wrap(int v,int size,boolean repeat)
	{
		if (repeat) { int r=v%size; return r<0?r+size:r; }
		return v<0?0:v>=size?size-1:v;
	}

	private static final class Vertex
	{
float x,y,invZ,depth,distance,u,v,r,g,b,a,clipX,clipY,clipZ,clipW;
		boolean valid;
void copyFrom(Vertex other)
{
x=other.x; y=other.y; invZ=other.invZ; depth=other.depth;
distance=other.distance; u=other.u; v=other.v;
r=other.r; g=other.g; b=other.b; a=other.a;
clipX=other.clipX; clipY=other.clipY; clipZ=other.clipZ; clipW=other.clipW;
valid=other.valid;
}
void interpolate(Vertex from,Vertex to,float amount)
{
clipX=from.clipX+(to.clipX-from.clipX)*amount;
clipY=from.clipY+(to.clipY-from.clipY)*amount;
clipZ=from.clipZ+(to.clipZ-from.clipZ)*amount;
clipW=from.clipW+(to.clipW-from.clipW)*amount;
distance=from.distance+(to.distance-from.distance)*amount;
u=from.u+(to.u-from.u)*amount; v=from.v+(to.v-from.v)*amount;
r=from.r+(to.r-from.r)*amount; g=from.g+(to.g-from.g)*amount;
b=from.b+(to.b-from.b)*amount; a=from.a+(to.a-from.a)*amount;
valid=false;
}
	}

	private static final class TextureData
	{
		final int[] pixels;
		final int width,height,blending,wrapS,wrapT,filter;
		final int blendR,blendG,blendB;
		TextureData(Texture2D texture)
		{
			Image2D image=texture.getImage();
			pixels=image==null?null:image.pixels();
			width=image==null?0:image.getWidth(); height=image==null?0:image.getHeight();
			blending=texture.getBlending(); wrapS=texture.getWrappingS(); wrapT=texture.getWrappingT();
			filter=texture.getImageFilter();
			int c=texture.getBlendColor(); blendR=(c>>>16)&255; blendG=(c>>>8)&255; blendB=c&255;
		}
		TextureData(Image2D image)
		{
			pixels=image==null?null:image.pixels();
			width=image==null?0:image.getWidth(); height=image==null?0:image.getHeight();
			blending=Texture2D.FUNC_REPLACE;
			wrapS=Texture2D.WRAP_CLAMP; wrapT=Texture2D.WRAP_CLAMP;
			filter=Texture2D.FILTER_NEAREST;
			blendR=blendG=blendB=0;
		}
		int sample(float u,float v)
		{
			float tx=wrapS==Texture2D.WRAP_REPEAT?u-(float)Math.floor(u):clamp(u);
			float ty=wrapT==Texture2D.WRAP_REPEAT?v-(float)Math.floor(v):clamp(v);
// Image2D scanlines and M3G texture coordinates both start at the top.
// Normalized coordinates address texel centers, not stretched image endpoints.
boolean repeatS=wrapS==Texture2D.WRAP_REPEAT, repeatT=wrapT==Texture2D.WRAP_REPEAT;
			if (filter==Texture2D.FILTER_NEAREST)
return pixels[wrap((int)Math.floor(ty*height),height,repeatT)*width
+wrap((int)Math.floor(tx*width),width,repeatS)];
float fx=tx*width-0.5f, fy=ty*height-0.5f;
int x0=(int)Math.floor(fx),y0=(int)Math.floor(fy);
			float dx=fx-x0,dy=fy-y0;
int x1=wrap(x0+1,width,repeatS),y1=wrap(y0+1,height,repeatT);
x0=wrap(x0,width,repeatS); y0=wrap(y0,height,repeatT);
			int c00=pixels[y0*width+x0],c10=pixels[y0*width+x1],c01=pixels[y1*width+x0],c11=pixels[y1*width+x1];
			return bilerp(c00,c10,c01,c11,dx,dy);
		}
		private static int bilerp(int a,int b,int c,int d,float x,float y)
		{
			int out=0;
			for(int shift=0;shift<=24;shift+=8) {
				float top=((a>>>shift)&255)*(1-x)+((b>>>shift)&255)*x;
				float bottom=((c>>>shift)&255)*(1-x)+((d>>>shift)&255)*x;
				out|=clampByte(Math.round(top*(1-y)+bottom*y))<<shift;
			}
			return out;
		}
	}
}

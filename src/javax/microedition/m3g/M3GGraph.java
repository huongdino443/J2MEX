package javax.microedition.m3g;

import java.util.*;

/** Reference traversal and node-subtree duplication, with shared geometry/material resources. */
final class M3GGraph {
    static List<Object3D> references(Object3D value) {
        List<Object3D> out=new ArrayList<Object3D>();
        out.addAll(value.tracks);
        if(value instanceof Group) {
            Group g=(Group)value;
            for(int i=0;i<g.getChildCount();i++) add(out,g.getChild(i));
        }
        if(value instanceof World) { add(out,((World)value).getActiveCamera()); add(out,((World)value).getBackground()); }
        if(value instanceof Mesh) {
            Mesh m=(Mesh)value; add(out,m.getVertexBuffer());
            for(int i=0;i<m.getSubmeshCount();i++) { add(out,m.getIndexBuffer(i)); add(out,m.getAppearance(i)); }
        }
        if(value instanceof SkinnedMesh) add(out,((SkinnedMesh)value).getSkeleton());
        if(value instanceof MorphingMesh) {
            MorphingMesh m=(MorphingMesh)value;
            for(int i=0;i<m.getMorphTargetCount();i++) add(out,m.getMorphTarget(i));
        }
        if(value instanceof Appearance) {
            Appearance a=(Appearance)value;
            add(out,a.getCompositingMode()); add(out,a.getFog()); add(out,a.getMaterial()); add(out,a.getPolygonMode());
            for(int i=0;i<8;i++) add(out,a.getTexture(i));
        }
        if(value instanceof Texture2D) add(out,((Texture2D)value).getImage());
        if(value instanceof Background) add(out,((Background)value).getImage());
        if(value instanceof Sprite3D) { add(out,((Sprite3D)value).getImage()); add(out,((Sprite3D)value).getAppearance()); }
        if(value instanceof AnimationTrack) {
            add(out,((AnimationTrack)value).getController()); add(out,((AnimationTrack)value).getKeyframeSequence());
        }
        if(value instanceof VertexBuffer) {
            VertexBuffer b=(VertexBuffer)value;
            add(out,b.getPositions(null)); add(out,b.getNormals()); add(out,b.getColors());
            for(int i=0;i<8;i++) add(out,b.getTexCoords(i,null));
        }
        return out;
    }
    private static void add(List<Object3D> out,Object3D value) { if(value!=null) out.add(value); }

    static Object3D find(Object3D root,int id) {
        Set<Object3D> visited=Collections.newSetFromMap(new IdentityHashMap<Object3D,Boolean>());
        Deque<Object3D> pending=new ArrayDeque<Object3D>(); pending.add(root);
        while(!pending.isEmpty()) {
            Object3D current=pending.removeFirst();
            if(!visited.add(current)) continue;
            if(current.getUserID()==id) return current;
            pending.addAll(references(current));
        }
        return null;
    }
    static Object3D duplicate(Object3D value) {
        return duplicate(value,new IdentityHashMap<Object3D,Object3D>());
    }
    private static Object3D duplicate(Object3D value,IdentityHashMap<Object3D,Object3D> copies) {
        Object3D previous=copies.get(value);
        if(previous!=null) return previous;
        Object3D copy;
        if(value instanceof World) copy=new World();
        else if(value instanceof Group) copy=new Group();
        else if(value instanceof SkinnedMesh) {
            SkinnedMesh m=(SkinnedMesh)value;
            IndexBuffer[] indices=new IndexBuffer[m.getSubmeshCount()];
            Appearance[] apps=new Appearance[indices.length];
            for(int i=0;i<indices.length;i++) {
                indices[i]=m.getIndexBuffer(i);
                apps[i]=m.getAppearance(i);
            }
            Group skeleton=(Group)duplicate(m.getSkeleton(),copies);
            SkinnedMesh d=new SkinnedMesh(m.getVertexBuffer(),indices,apps,skeleton);
            for(SkinnedMesh.BoneBinding binding:m.boneBindings()) {
                Node bone=(Node)copies.get(binding.bone);
                if(bone==null) throw new IllegalStateException("SkinnedMesh duplicate lost a bone");
                d.addTransform(bone,binding.weight,binding.firstVertex,
                        binding.numVertices,binding.toBone);
            }
            copy=d;
        }
        else if(value instanceof MorphingMesh) {
            MorphingMesh m=(MorphingMesh)value;
            IndexBuffer[] indices=new IndexBuffer[m.getSubmeshCount()];
            Appearance[] apps=new Appearance[indices.length];
            for(int i=0;i<indices.length;i++) { indices[i]=m.getIndexBuffer(i); apps[i]=m.getAppearance(i); }
            VertexBuffer[] targets=new VertexBuffer[m.getMorphTargetCount()];
            for(int i=0;i<targets.length;i++) targets[i]=m.getMorphTarget(i);
            MorphingMesh d=new MorphingMesh(m.getVertexBuffer(),targets,indices,apps);
            float[] weights=new float[targets.length]; m.getWeights(weights); d.setWeights(weights);
            copy=d;
        }
        else if(value instanceof Mesh && value.getClass()==Mesh.class) {
            Mesh m=(Mesh)value; IndexBuffer[] indices=new IndexBuffer[m.getSubmeshCount()];
            Appearance[] apps=new Appearance[indices.length];
            for(int i=0;i<indices.length;i++) { indices[i]=m.getIndexBuffer(i); apps[i]=m.getAppearance(i); }
            copy=new Mesh(m.getVertexBuffer(),indices,apps);
        } else if(value instanceof Camera) {
            Camera c=(Camera)value,d=new Camera(); float[] p=new float[4]; int kind=c.getProjection(p);
            if(kind==Camera.GENERIC) { Transform t=new Transform(); c.getProjection(t); d.setGeneric(t); }
            else if(kind==Camera.PARALLEL) d.setParallel(p[0],p[1],p[2],p[3]);
            else d.setPerspective(p[0],p[1],p[2],p[3]);
            copy=d;
        } else if(value instanceof Light) {
            Light l=(Light)value,d=new Light(); d.setMode(l.getMode()); d.setColor(l.getColor()); d.setIntensity(l.getIntensity());
            d.setAttenuation(l.getConstantAttenuation(),l.getLinearAttenuation(),l.getQuadraticAttenuation());
            d.setSpotAngle(l.getSpotAngle()); d.setSpotExponent(l.getSpotExponent()); copy=d;
        } else if(value instanceof Sprite3D) {
            Sprite3D s=(Sprite3D)value,d=new Sprite3D(s.isScaled(),s.getImage(),s.getAppearance());
            d.setCrop(s.getCropX(),s.getCropY(),s.getCropWidth(),s.getCropHeight()); copy=d;
        } else if(value instanceof Texture2D) {
            Texture2D t=(Texture2D)value,d=new Texture2D(t.getImage());
            d.setBlendColor(t.getBlendColor()); d.setBlending(t.getBlending());
            d.setFiltering(t.getLevelFilter(),t.getImageFilter());
            d.setWrapping(t.getWrappingS(),t.getWrappingT()); copy=d;
        } else if(value instanceof PolygonMode) {
            PolygonMode p=(PolygonMode)value,d=new PolygonMode();
            d.setCulling(p.getCulling()); d.setShading(p.getShading()); d.setWinding(p.getWinding());
            d.setLocalCameraLightingEnable(p.isLocalCameraLightingEnabled());
            d.setPerspectiveCorrectionEnable(p.isPerspectiveCorrectionEnabled());
            d.setTwoSidedLightingEnable(p.isTwoSidedLightingEnabled()); copy=d;
        } else if(value instanceof Appearance) {
            Appearance a=(Appearance)value,d=new Appearance();
            d.setLayer(a.getLayer()); d.setCompositingMode(a.getCompositingMode()); d.setFog(a.getFog());
            d.setPolygonMode(a.getPolygonMode()); d.setMaterial(a.getMaterial());
            for(int i=0;i<8;i++) d.setTexture(i,a.getTexture(i)); copy=d;
        } else if(value instanceof Material) {
            Material m=(Material)value,d=new Material();
            for(int target:new int[]{Material.AMBIENT,Material.DIFFUSE,Material.EMISSIVE,Material.SPECULAR})
                d.setColor(target,m.getColor(target));
            d.setShininess(m.getShininess()); d.setVertexColorTrackingEnable(m.isVertexColorTrackingEnabled()); copy=d;
        } else if(value instanceof AnimationTrack) {
            AnimationTrack t=(AnimationTrack)value,d=new AnimationTrack(t.getKeyframeSequence(),t.getTargetProperty());
            d.setController(t.getController()); copy=d;
        } else throw new UnsupportedOperationException("M3G duplicate not implemented for "+value.getClass().getName());
        copies.put(value,copy);
        copy.setUserID(value.getUserID()); copy.setUserObject(value.getUserObject());
        for(AnimationTrack track:value.tracks) copy.addAnimationTrack((AnimationTrack)duplicate(track,copies));
        if(value instanceof Transformable) {
            ((Transformable)value).copyTransformableStateTo((Transformable)copy);
        }
        if(value instanceof Node) {
            Node n=(Node)value,d=(Node)copy; d.setRenderingEnable(n.isRenderingEnabled()); d.setPickingEnable(n.isPickingEnabled());
            d.setAlphaFactor(n.getAlphaFactor()); d.setScope(n.getScope());
        }
        if(value instanceof Group) {
            Group g=(Group)value,d=(Group)copy;
            for(Node child:g.children()) d.addChild((Node)duplicate(child,copies));
        }
        if(value instanceof World) {
            World w=(World)value,d=(World)copy;
            Camera camera=(Camera)copies.get(w.getActiveCamera());
            d.setActiveCamera(camera==null?w.getActiveCamera():camera); d.setBackground(w.getBackground());
        }
        return copy;
    }
}

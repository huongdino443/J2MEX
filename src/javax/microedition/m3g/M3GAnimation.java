package javax.microedition.m3g;

import java.util.*;

/** Basic linear/step/quaternion scene tracks; advanced interpolation is not silently substituted. */
final class M3GAnimation {
    static int animate(Object3D root,int time) {
        Set<Object3D> seen=Collections.newSetFromMap(new IdentityHashMap<Object3D,Boolean>());
        Deque<Object3D> pending=new ArrayDeque<Object3D>(); pending.add(root);
        boolean animated=false;
        while(!pending.isEmpty()) {
            Object3D value=pending.removeFirst();
            if(!seen.add(value)) continue;
            for(AnimationTrack track:value.tracks) {
                AnimationController controller=track.getController();
                if(controller==null||controller.getWeight()<=0) continue;
                int start=controller.getActiveIntervalStart(),end=controller.getActiveIntervalEnd();
                if(start!=end&&(time<start||time>=end)) continue;
                float[] sample=track.getKeyframeSequence().sample(controller.getPosition(time));
                apply(value,track.getTargetProperty(),sample); animated=true;
            }
            pending.addAll(M3GGraph.references(value));
        }
        return animated?1:Integer.MAX_VALUE;
    }
    private static void apply(Object3D value,int property,float[] sample) {
        if(value instanceof Transformable) {
            Transformable t=(Transformable)value;
            if(property==AnimationTrack.TRANSLATION) { t.setTranslation(sample[0],sample[1],sample[2]); return; }
            if(property==AnimationTrack.SCALE) {
                t.setScale(sample[0],sample.length==1?sample[0]:sample[1],sample.length==1?sample[0]:sample[2]); return;
            }
            if(property==AnimationTrack.ORIENTATION) {
                float x=sample[0], y=sample[1], z=sample[2], w=sample[3];
                float norm=(float)Math.sqrt(x*x+y*y+z*z+w*w);
                if(!(norm>0.000001f)||!Float.isFinite(norm)) {
                    t.setOrientation(0,0,1,0);
                    return;
                }
                x/=norm; y/=norm; z/=norm; w/=norm;
                w=Math.max(-1,Math.min(1,w));
                float sine=(float)Math.sqrt(Math.max(0,1-w*w));
                if(sine<0.00001f) t.setOrientation(0,0,1,0);
                else t.setOrientation((float)Math.toDegrees(2*Math.acos(w)),x/sine,y/sine,z/sine);
                return;
            }
        }
        if(value instanceof Node) {
            Node n=(Node)value;
            if(property==AnimationTrack.ALPHA) { n.setAlphaFactor(Math.max(0,Math.min(1,sample[0]))); return; }
            if(property==AnimationTrack.VISIBILITY) { n.setRenderingEnable(sample[0]>=0.5f); return; }
            if(property==AnimationTrack.PICKABILITY) { n.setPickingEnable(sample[0]>=0.5f); return; }
        }
        if(value instanceof MorphingMesh&&property==AnimationTrack.MORPH_WEIGHTS) {
            ((MorphingMesh)value).setWeights(sample); return;
        }
        if(value instanceof Material) {
            Material m=(Material)value; int target=property==AnimationTrack.AMBIENT_COLOR?Material.AMBIENT:
                    property==AnimationTrack.DIFFUSE_COLOR?Material.DIFFUSE:
                    property==AnimationTrack.EMISSIVE_COLOR?Material.EMISSIVE:
                    property==AnimationTrack.SPECULAR_COLOR?Material.SPECULAR:0;
            if(target!=0) { m.setColor(target,color(sample)); return; }
            if(property==AnimationTrack.SHININESS) { m.setShininess(sample[0]); return; }
        }
        if(value instanceof Light) {
            Light l=(Light)value;
            if(property==AnimationTrack.COLOR) { l.setColor(color(sample)); return; }
            if(property==AnimationTrack.INTENSITY) { l.setIntensity(sample[0]); return; }
        }
        throw new UnsupportedOperationException("M3G animation property "+property+" on "+value.getClass().getName());
    }
    private static int color(float[] v) {
        int r=channel(v[0]),g=channel(v[1]),b=channel(v[2]),a=v.length>3?channel(v[3]):255;
        return a<<24|r<<16|g<<8|b;
    }
    private static int channel(float v) { return Math.max(0,Math.min(255,Math.round(v*255))); }
}

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

public class KeyframeSequence extends Object3D
{

	public static final int CONSTANT = 192;
	public static final int LINEAR = 176;
	public static final int LOOP = 193;
	public static final int SLERP = 177;
	public static final int SPLINE = 178;
	public static final int SQUAD = 179;
	public static final int STEP = 180;


	private int duration;
	private int intType;
	private int keyframes;
	private int repeat;
	private int rangeFirst;
	private int rangeLast;
private int components;
private int[] times;
private float[][] values;


public KeyframeSequence(int numKeyframes, int numComponents, int interpolation) {
if(numKeyframes<1||numComponents<1) throw new IllegalArgumentException("Empty keyframe sequence");
keyframes=numKeyframes; components=numComponents; intType=interpolation;
times=new int[numKeyframes]; values=new float[numKeyframes][numComponents];
rangeLast=numKeyframes-1; repeat=CONSTANT;
}


public int getComponentCount() { return components; }

	public int getDuration() { return duration; }

	public int getInterpolationType() { return intType; }

public int getKeyframe(int index, float[] value) {
if(value!=null) System.arraycopy(values[index],0,value,0,components);
return times[index];
}

	public int getKeyframeCount() { return keyframes; }

	public int getRepeatMode() { return repeat; }

	public int getValidRangeFirst() { return rangeFirst; }

	public int getValidRangeLast() { return rangeLast; }

	public void setDuration(int value) { duration = value; }

public void setKeyframe(int index, int time, float[] value) {
if(time<0) throw new IllegalArgumentException("Negative keyframe time");
System.arraycopy(value,0,values[index],0,components); times[index]=time;
}

	public void setRepeatMode(int mode) { repeat=mode; }

public void setValidRange(int first, int last) {
if(first<0||first>=keyframes||last<0||last>=keyframes) throw new IndexOutOfBoundsException("keyframe range");
rangeFirst=first; rangeLast=last;
}

float[] sample(float time) {
boolean loop=repeat==LOOP&&duration>0;
if(loop) {
time=(time%duration+duration)%duration;
if(time<times[rangeFirst]) time+=duration;
} else {
time=Math.max(0,Math.min(duration,time));
if(time<times[rangeFirst]) return values[rangeFirst].clone();
if(time>=times[rangeLast]) return values[rangeLast].clone();
}
int a=rangeFirst;
for(int i=0;i<keyframes;i++) {
int b=nextKeyframeIndex(a);
if(a==rangeLast&&!loop) break;
float next=times[b]; if(b<=a) next+=duration;
float now=time; if(now<times[a]) now+=duration;
if(now<=next) {
float factor=next==times[a]?0:(now-times[a])/(next-times[a]);
return interpolate(a,b,Math.max(0,Math.min(1,factor)));
}
a=b;
if(a==rangeFirst) break;
}
return values[a].clone();
}

private int nextKeyframeIndex(int index) {
if(index==rangeLast) return rangeFirst;
return index==keyframes-1?0:index+1;
}

private int previousKeyframeIndex(int index) {
if(index==rangeFirst) return rangeLast;
return index==0?keyframes-1:index-1;
}

private long timeDelta(int index) {
int next=nextKeyframeIndex(index);
long delta=(long)times[next]-times[index];
if(next<=index) delta+=duration;
return delta;
}

private float[] tangentAt(int index,boolean incoming) {
float[] tangent=new float[components];
if(repeat!=LOOP&&(index==rangeFirst||index==rangeLast)) return tangent;
int previous=previousKeyframeIndex(index),next=nextKeyframeIndex(index);
long previousDelta=timeDelta(previous),nextDelta=timeDelta(index);
long total=previousDelta+nextDelta;
if(total<=0) return tangent;
float scale=(incoming?2f*previousDelta:2f*nextDelta)/(float)total;
for(int i=0;i<components;i++)
tangent[i]=0.5f*(values[next][i]-values[previous][i])*scale;
return tangent;
}

private float[] interpolate(int start,int end,float t) {
float[] a=values[start],b=values[end];
if(intType==STEP) return a.clone();
if(intType==SPLINE) return spline(start,end,t);
if(intType!=LINEAR&&intType!=SLERP)
throw new UnsupportedOperationException("M3G interpolation not implemented: "+intType);
float[] result=new float[components];
float left=1-t,right=t;
if(intType==SLERP&&components==4) {
float dot=0; for(int i=0;i<4;i++) dot+=a[i]*b[i];
float sign=dot<0?-1:1; dot=Math.min(1,Math.abs(dot));
if(dot<0.9995f) {
double angle=Math.acos(dot),sin=Math.sin(angle);
left=(float)(Math.sin((1-t)*angle)/sin); right=(float)(Math.sin(t*angle)/sin);
}
right*=sign;
}
float norm=0;
for(int i=0;i<components;i++) { result[i]=a[i]*left+b[i]*right; norm+=result[i]*result[i]; }
if(intType==SLERP&&norm>0) { norm=(float)Math.sqrt(norm); for(int i=0;i<components;i++) result[i]/=norm; }
return result;
}

private float[] spline(int start,int end,float t) {
float[] startTangent=tangentAt(start,false);
float[] endTangent=tangentAt(end,true);
float t2=t*t,t3=t2*t;
float hStart=2*t3-3*t2+1;
float hEnd=-2*t3+3*t2;
float hStartTangent=t3-2*t2+t;
float hEndTangent=t3-t2;
float[] result=new float[components];
for(int i=0;i<components;i++)
result[i]=hStart*values[start][i]+hEnd*values[end][i]
        +hStartTangent*startTangent[i]+hEndTangent*endTangent[i];
return result;
}

}

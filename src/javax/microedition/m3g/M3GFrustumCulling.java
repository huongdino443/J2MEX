/*
This file is part of FreeJ2ME.

FreeJ2ME is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
*/
package javax.microedition.m3g;

import java.util.IdentityHashMap;
import java.util.WeakHashMap;

final class M3GFrustumCulling
{
	private static final boolean PROFILE_ENABLED =
			Boolean.getBoolean("j2mex.render.profile");
	private static final int PROFILE_WINDOW_TARGETS = 120;
	private static final int REUSE_SAMPLE_INTERVAL = 8;

	private final float[] bounds = new float[6];
	private final float[] modelMatrix = new float[16];
	private final float[] mvp = new float[16];
	private final float[] cameraClip = new float[16];
	private final float[] projectionMatrix = new float[16];
	private final float[] cameraWorldSignature = new float[16];
	private final float[] cameraWorldCurrent = new float[16];
	private final float[] viewMatrix = new float[16];
	private final Transform viewTransform = new Transform();
	private final Transform projectionTransform = new Transform();
	private final Camera fallbackCamera = new Camera();
	private final float[] positionScaleBias = new float[4];
	private final float[] textureScaleBias = new float[3];
	private final IdentityHashMap<VertexBuffer, Boolean> sampledBuffers =
			new IdentityHashMap<VertexBuffer, Boolean>();
	private final WeakHashMap<VertexBuffer, long[]> sourceStamps =
			new WeakHashMap<VertexBuffer, long[]>();

	private Camera cachedCamera;
	private int cachedWidth = -1;
	private int cachedHeight = -1;
	private boolean cameraStateKnown;
	private long targetTests;
	private long targetCulled;
	private long windowTests;
	private long windowCulled;
	private int windowTargets;
	private long targetSequence;
	private boolean sampleReuseThisTarget;
	private long sampleUniqueBuffers;
	private long sampleRepeatedDraws;
	private long sampleFirstSeenDraws;
	private long sampleUnchangedAcrossTargets;
	private long sampleChangedDraws;
	private long windowReuseSamples;
	private long windowUniqueBuffers;
	private long windowRepeatedDraws;
	private long windowFirstSeenDraws;
	private long windowUnchangedAcrossTargets;
	private long windowChangedDraws;

	void beginTarget()
	{
		targetTests = 0;
		targetCulled = 0;
		targetSequence++;
		sampleReuseThisTarget = PROFILE_ENABLED
				&& targetSequence % REUSE_SAMPLE_INTERVAL == 0;
		sampleUniqueBuffers = 0;
		sampleRepeatedDraws = 0;
		sampleFirstSeenDraws = 0;
		sampleUnchangedAcrossTargets = 0;
		sampleChangedDraws = 0;
		if (sampleReuseThisTarget) sampledBuffers.clear();
	}

	void endTarget()
	{
		if (!PROFILE_ENABLED) return;
		windowTests += targetTests;
		windowCulled += targetCulled;
		if (sampleReuseThisTarget) {
			windowReuseSamples++;
			windowUniqueBuffers += sampleUniqueBuffers;
			windowRepeatedDraws += sampleRepeatedDraws;
			windowFirstSeenDraws += sampleFirstSeenDraws;
			windowUnchangedAcrossTargets += sampleUnchangedAcrossTargets;
			windowChangedDraws += sampleChangedDraws;
		}
		if (++windowTargets < PROFILE_WINDOW_TARGETS) return;

		double targets = windowTargets;
		System.out.println(String.format(java.util.Locale.US,
				"[m3g-cull] targets=%d meshes/target=%.1f culled/target=%.1f " +
				"reuseSamples=%d uniqueBuffers/sample=%.1f repeatedDraws/sample=%.1f " +
				"firstSeen/sample=%.1f unchangedAcrossTargets/sample=%.1f changedData/sample=%.1f",
				windowTargets, windowTests / targets, windowCulled / targets,
				windowReuseSamples,
				average(windowUniqueBuffers, windowReuseSamples),
				average(windowRepeatedDraws, windowReuseSamples),
				average(windowFirstSeenDraws, windowReuseSamples),
				average(windowUnchangedAcrossTargets, windowReuseSamples),
				average(windowChangedDraws, windowReuseSamples)));
		windowTargets = 0;
		windowTests = 0;
		windowCulled = 0;
		windowReuseSamples = 0;
		windowUniqueBuffers = 0;
		windowRepeatedDraws = 0;
		windowFirstSeenDraws = 0;
		windowUnchangedAcrossTargets = 0;
		windowChangedDraws = 0;
	}

	boolean shouldCull(VertexBuffer vertices, Transform model, Camera camera,
			Transform cameraWorld, int width, int height)
	{
		if (vertices == null) return false;
		if (PROFILE_ENABLED) targetTests++;
		if (!vertices.getPositionBounds(bounds)) return false;
		if (sampleReuseThisTarget) recordSourceReuse(vertices);
		prepareCamera(camera, cameraWorld, width, height);

		if (model == null) {
			setIdentity(modelMatrix);
		} else {
			model.getColumnMajor(modelMatrix);
		}
		multiplyColumnMajor(mvp, cameraClip, modelMatrix);
		boolean outside = isAabbOutsideClipVolume(bounds, mvp);
		if (outside && PROFILE_ENABLED) targetCulled++;
		return outside;
	}

	private void recordSourceReuse(VertexBuffer vertices)
	{
		if (sampledBuffers.put(vertices, Boolean.TRUE) == null)
			sampleUniqueBuffers++;
		else
			sampleRepeatedDraws++;

		long stamp = sourceStamp(vertices);
		long[] previous = sourceStamps.get(vertices);
		if (previous == null) {
			sourceStamps.put(vertices, new long[] { stamp, targetSequence });
			sampleFirstSeenDraws++;
			return;
		}

		if (previous[0] == stamp) {
			if (previous[1] != targetSequence) sampleUnchangedAcrossTargets++;
		} else {
			sampleChangedDraws++;
			previous[0] = stamp;
		}
		previous[1] = targetSequence;
	}

	private long sourceStamp(VertexBuffer vertices)
	{
		long hash = 0xcbf29ce484222325L;
		VertexArray positions = vertices.getPositions(positionScaleBias);
		VertexArray normals = vertices.getNormals();
		VertexArray colors = vertices.getColors();
		textureScaleBias[0] = textureScaleBias[1] = textureScaleBias[2] = 0f;
		VertexArray textureCoords = vertices.getTexCoords(0, textureScaleBias);
		hash = mix(hash, positions);
		hash = mix(hash, normals);
		hash = mix(hash, colors);
		hash = mix(hash, textureCoords);
		for (float value : positionScaleBias)
			hash = mix(hash, Float.floatToIntBits(value));
		for (float value : textureScaleBias)
			hash = mix(hash, Float.floatToIntBits(value));
		return mix(hash, vertices.getDefaultColor());
	}

	private static long mix(long hash, VertexArray array)
	{
		hash = mix(hash, System.identityHashCode(array));
		return array == null ? hash : mix(hash, array.getVersion());
	}

	private static long mix(long hash, long value)
	{
		return (hash ^ value) * 0x100000001b3L;
	}

	private static double average(long count, long samples)
	{
		return samples == 0 ? 0 : count / (double) samples;
	}

	private void prepareCamera(Camera camera, Transform cameraWorld,
			int width, int height)
	{
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		if (cameraWorld == null) setIdentity(cameraWorldCurrent);
		else cameraWorld.getColumnMajor(cameraWorldCurrent);

		boolean changed = !cameraStateKnown || cachedCamera != camera
				|| cachedWidth != safeWidth || cachedHeight != safeHeight;
		if (!changed) {
			for (int i = 0; i < 16; i++) {
				if (cameraWorldCurrent[i] != cameraWorldSignature[i]) {
					changed = true;
					break;
				}
			}
		}
		if (!changed) return;

		System.arraycopy(cameraWorldCurrent, 0, cameraWorldSignature, 0, 16);
		cachedCamera = camera;
		cachedWidth = safeWidth;
		cachedHeight = safeHeight;
		cameraStateKnown = true;

		viewTransform.setColumnMajor(cameraWorldCurrent);
		try {
			viewTransform.invert();
		} catch (ArithmeticException singular) {
			viewTransform.setIdentity();
		}
		viewTransform.getColumnMajor(viewMatrix);

		Camera activeCamera = camera;
		if (activeCamera == null) {
			fallbackCamera.setPerspective(45f,
					safeWidth / (float) safeHeight, 0.1f, 10000f);
			activeCamera = fallbackCamera;
		}
		activeCamera.getProjection(projectionTransform);
		projectionTransform.getColumnMajor(projectionMatrix);
		multiplyColumnMajor(cameraClip, projectionMatrix, viewMatrix);
	}

	static boolean isAabbOutsideClipVolume(float[] bounds, float[] mvp)
	{
		if (bounds == null || bounds.length < 6 || mvp == null || mvp.length < 16)
			throw new IllegalArgumentException("AABB and clip matrix are required");

		int commonOutside = 0x3f;
		boolean allBehind = true;
		for (int corner = 0; corner < 8; corner++) {
			float x = bounds[(corner & 1) == 0 ? 0 : 3];
			float y = bounds[(corner & 2) == 0 ? 1 : 4];
			float z = bounds[(corner & 4) == 0 ? 2 : 5];
			float clipX = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12];
			float clipY = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13];
			float clipZ = mvp[2] * x + mvp[6] * y + mvp[10] * z + mvp[14];
			float clipW = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15];
			if (!Float.isFinite(clipX) || !Float.isFinite(clipY)
					|| !Float.isFinite(clipZ) || !Float.isFinite(clipW)) return false;

			allBehind &= clipW <= 0f;
			float epsilon = 0.00001f * Math.max(1f, Math.abs(clipW));
			int outside = 0;
			if (clipX < -clipW - epsilon) outside |= 1;
			if (clipX > clipW + epsilon) outside |= 2;
			if (clipY < -clipW - epsilon) outside |= 4;
			if (clipY > clipW + epsilon) outside |= 8;
			if (clipZ < -clipW - epsilon) outside |= 16;
			if (clipZ > clipW + epsilon) outside |= 32;
			commonOutside &= outside;
		}
		return allBehind || commonOutside != 0;
	}

	private static void multiplyColumnMajor(float[] result, float[] left, float[] right)
	{
		for (int column = 0; column < 4; column++) {
			int offset = column * 4;
			for (int row = 0; row < 4; row++) {
				result[offset + row] = left[row] * right[offset]
						+ left[4 + row] * right[offset + 1]
						+ left[8 + row] * right[offset + 2]
						+ left[12 + row] * right[offset + 3];
			}
		}
	}

	private static void setIdentity(float[] matrix)
	{
		java.util.Arrays.fill(matrix, 0f);
		matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1f;
	}
}

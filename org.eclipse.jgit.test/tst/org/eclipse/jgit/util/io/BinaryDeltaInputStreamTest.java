/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.junit.Test;

/**
 * Crude tests for the {@link BinaryDeltaInputStream} using delta diffs
 * generated by C git.
 */
public class BinaryDeltaInputStreamTest {

	private InputStream getBinaryHunk(String name) {
		return this.getClass().getResourceAsStream(name);
	}

	@Test
	public void testBinaryDelta() throws Exception {
		// Prepare our test data
		byte[] data = new byte[8192];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) (255 - (i % 256));
		}
		// Same, but with five 'x' inserted in the middle.
		int middle = data.length / 2;
		byte[] newData = new byte[data.length + 5];
		System.arraycopy(data, 0, newData, 0, middle);
		for (int i = 0; i < 5; i++) {
			newData[middle + i] = 'x';
		}
		System.arraycopy(data, middle, newData, middle + 5, middle);
		// delta1.forward has the instructions
		// @formatter:off
		// COPY 0 4096
		// INSERT 5 xxxxx
		// COPY 0 4096
		// @formatter:on
		// Note that the way we built newData could be expressed as
		// @formatter:off
		// COPY 0 4096
		// INSERT 5 xxxxx
		// COPY 4096 4096
		// @formatter:on
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				BinaryDeltaInputStream input = new BinaryDeltaInputStream(data,
						new InflaterInputStream(new BinaryHunkInputStream(
								getBinaryHunk("delta1.forward"))))) {
			byte[] buf = new byte[1024];
			int n;
			while ((n = input.read(buf)) >= 0) {
				out.write(buf, 0, n);
			}
			assertArrayEquals(newData, out.toByteArray());
			assertTrue(input.isFullyConsumed());
		}
		// delta1.reverse has the instructions
		// @formatter:off
		// COPY 0 4096
		// COPY 256 3840
		// COPY 256 256
		// @formatter:on
		// Note that there are alternatives, for instance
		// @formatter:off
		// COPY 0 4096
		// COPY 4101 4096
		// @formatter:on
		// or
		// @formatter:off
		// COPY 0 4096
		// COPY 0 4096
		// @formatter:on
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				BinaryDeltaInputStream input = new BinaryDeltaInputStream(
						newData,
						new InflaterInputStream(new BinaryHunkInputStream(
								getBinaryHunk("delta1.reverse"))))) {
			long expectedSize = input.getExpectedResultSize();
			assertEquals(data.length, expectedSize);
			byte[] buf = new byte[1024];
			int n;
			while ((n = input.read(buf)) >= 0) {
				out.write(buf, 0, n);
			}
			assertArrayEquals(data, out.toByteArray());
			assertTrue(input.isFullyConsumed());
		}
	}
}
/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2016 Board of Regents of the University of
 * Wisconsin-Madison
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package io.scif.services;

import io.scif.AxisGuesser;
import io.scif.FilePattern;
import io.scif.NumberFilter;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.scijava.io.Location;
import org.scijava.io.handles.FileLocation;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;
import org.scijava.util.ArrayUtils;

/**
 * Default {@link FilePatternService} implementation.
 *
 * @see FilePatternService
 * @author Mark Hiner
 */
@Plugin(type = Service.class)
public class DefaultFilePatternService extends AbstractService implements
	FilePatternService
{

	@Override
	public String findPattern(final String path) throws IOException {
		return findPattern(new FileLocation(path));
	}

	@Override
	public String findPattern(final Location file) throws IOException {
		if (file.isBrowsable()) {
			return findPattern(file, file.getParent());
		}
		throw new IOException("FilePatterns require a browsable Location!");
	}

	@Override
	public String findPattern(final Location name, Location dir)
		throws IOException
	{

		// list files in the given directory
		final Set<Location> f = dir.getChildren();
		if (f.isEmpty()) return null;
		return findPattern(name, dir, f);
	}

	@Override
	public String findPattern(final Location name, final Location dir,
		final Collection<Location> f)
	{
		return findPattern(name, dir, f, null);
	}

	@Override
	public String findPattern(final Location file, Location dir,
		final Collection<Location> nameList, int[] excludeAxes)
	{
		if (excludeAxes == null) excludeAxes = new int[0];

		String name = file.getName();

		// compile list of numerical blocks
		final int len = name.length();
		final int bound = (len + 1) / 2;
		final int[] indexList = new int[bound];
		final int[] endList = new int[bound];
		int q = 0;
		boolean num = false;
		int ndx = -1;
		int e = 0;
		for (int i = 0; i < len; i++) {
			final char c = name.charAt(i);
			if (c >= '0' && c <= '9') {
				if (num) e++;
				else {
					num = true;
					ndx = i;
					e = ndx + 1;
				}
			}
			else if (num) {
				num = false;
				indexList[q] = ndx;
				endList[q] = e;
				q++;
			}
		}
		if (num) {
			indexList[q] = ndx;
			endList[q] = e;
			q++;
		}

		// analyze each block, building pattern as we go
		final StringBuilder sb = new StringBuilder("");

		for (int i = 0; i < q; i++) {
			final int last = i > 0 ? endList[i - 1] : 0;
			final String prefix = name.substring(last, indexList[i]);
			final int axisType = AxisGuesser.getAxisType(prefix);
			if (ArrayUtils.contains(excludeAxes, axisType)) {
				sb.append(name.substring(last, endList[i]));
				continue;
			}

			sb.append(prefix);
			final String pre = name.substring(0, indexList[i]);
			final String post = name.substring(endList[i]);

			final NumberFilter filter = new NumberFilter(pre, post);
			final String[] list = matchFiles(nameList, filter);
			if (list == null || list.length == 0) return null;
			if (list.length == 1) {
				// false alarm; this number block is constant
				sb.append(name.substring(indexList[i], endList[i]));
				continue;
			}
			boolean fix = true;
			for (final String s : list) {
				if (s.length() != len) {
					fix = false;
					break;
				}
			}
			if (fix) {
				// tricky; this fixed-width block could represent multiple
				// numberings
				final int width = endList[i] - indexList[i];

				// check each character for duplicates
				final boolean[] same = new boolean[width];
				for (int j = 0; j < width; j++) {
					same[j] = true;
					final int jx = indexList[i] + j;
					final char c = name.charAt(jx);
					for (final String s : list) {
						if (s.charAt(jx) != c) {
							same[j] = false;
							break;
						}
					}
				}

				// break down each sub-block
				int j = 0;
				while (j < width) {
					final int jx = indexList[i] + j;
					if (same[j]) {
						sb.append(name.charAt(jx));
						j++;
					}
					else {
						while (j < width && !same[j])
							j++;
						final String p = findPattern(name, nameList, jx, indexList[i] + j,
							"");
						final char c = indexList[i] > 0 ? name.charAt(indexList[i] - 1)
							: '.';
						// check if this block represents the series axis
						if (p == null && c != 'S' && c != 's' && c != 'E' && c != 'e') {
							// unable to find an appropriate breakdown of
							// numerical blocks
							return null;
						}
						else if (p == null) {
							sb.append(name.charAt(endList[i] - 1));
						}
						else sb.append(p);
					}
				}
			}
			else {
				// assume variable-width block represents only one numbering
				final BigInteger[] numbers = new BigInteger[list.length];
				for (int j = 0; j < list.length; j++) {
					numbers[j] = filter.getNumber(list[j]);
				}
				Arrays.sort(numbers);
				final String bounds = getBounds(numbers, false);
				if (bounds == null) return null;
				sb.append(bounds);
			}
		}
		sb.append(q > 0 ? name.substring(endList[q - 1]) : name);

		for (int i = 0; i < sb.length(); i++) {
			if (sb.charAt(i) == '\\') {
				sb.insert(i, '\\');
				i++;
			}
		}

		return sb.toString();
	}

	@Override
	public String findPattern(final String[] names) {
		final String dir = names[0].substring(0, names[0].lastIndexOf(
			File.separator) + 1);

		final StringBuilder pattern = new StringBuilder();
		pattern.append(Pattern.quote(dir));

		for (int i = 0; i < names.length; i++) {
			pattern.append("(?:");
			final String name = names[i].substring(names[i].lastIndexOf(
				File.separator) + 1);
			pattern.append(Pattern.quote(name));
			pattern.append(")");
			if (i < names.length - 1) {
				pattern.append("|");
			}
		}
		return pattern.toString();
	}

	@Override
	public String[] findImagePatterns(final Location base) throws IOException {
		final Location file = base;
		final Location parent = file.getParent();
		final Set<Location> list = parent.getChildren();
		return findImagePatterns(base, parent, list);
	}

	@Override
	public String[] findImagePatterns(final Location base, final Location dir,
		final Collection<Location> nameList) throws IOException
	{
		String name = base.getName();
		int dot = name.lastIndexOf('.');
		String baseSuffix;
		if (dot < 0) baseSuffix = "";
		else baseSuffix = name.substring(dot + 1);

		final List<String> patterns = new ArrayList<>();
		final int[] exclude = new int[] { AxisGuesser.S_AXIS };
		for (final Location loc : nameList) {
			final String pattern = findPattern(loc, dir, nameList, exclude);
			if (pattern == null) continue;
			int start = pattern.lastIndexOf(File.separator) + 1;
			if (start < 0) start = 0;
			String patternSuffix = pattern.substring(start);
			dot = patternSuffix.indexOf('.');
			if (dot < 0) patternSuffix = "";
			else patternSuffix = patternSuffix.substring(dot + 1);

			final String checkPattern = findPattern(loc, dir, nameList);
			final Location[] checkFiles = new FilePattern(base, checkPattern)
				.getFiles();

			if (!patterns.contains(pattern) && (!base.getSibling(pattern).exists()) &&
				patternSuffix.equals(baseSuffix) && ArrayUtils.indexOf(checkFiles,
					base) >= 0)
			{
				patterns.add(pattern);
			}
		}
		final String[] s = patterns.toArray(new String[patterns.size()]);
		Arrays.sort(s);
		return s;
	}

	// -- Utility helper methods --

	/** Recursive method for parsing a fixed-width numerical block. */
	private String findPattern(final String name,
		final Collection<Location> nameList, final int ndx, final int end,
		final String p)
	{
		if (ndx == end) return p;
		for (int i = end - ndx; i >= 1; i--) {
			final NumberFilter filter = new NumberFilter(name.substring(0, ndx), name
				.substring(ndx + i));
			final String[] list = matchFiles(nameList, filter);
			final BigInteger[] numbers = new BigInteger[list.length];
			for (int j = 0; j < list.length; j++) {
				numbers[j] = new BigInteger(list[j].substring(ndx, ndx + i));
			}
			Arrays.sort(numbers);
			final String bounds = getBounds(numbers, true);
			if (bounds == null) continue;
			final String pat = findPattern(name, nameList, ndx + i, end, p + bounds);
			if (pat != null) return pat;
		}
		// no combination worked; this parse path is infeasible
		return null;
	}

	/**
	 * Gets a string containing start, end and step values for a sorted list of
	 * numbers.
	 */
	private String getBounds(final BigInteger[] numbers, final boolean fixed) {
		if (numbers.length < 2) return null;
		final BigInteger b = numbers[0];
		final BigInteger e = numbers[numbers.length - 1];
		final BigInteger s = numbers[1].subtract(b);
		if (s.equals(BigInteger.ZERO)) {
			// step size must be positive
			return null;
		}
		for (int i = 2; i < numbers.length; i++) {
			if (!numbers[i].subtract(numbers[i - 1]).equals(s)) {
				// step size is not constant
				return null;
			}
		}
		final String sb = b.toString();
		final String se = e.toString();
		final StringBuilder bounds = new StringBuilder("<");
		if (fixed) {
			final int zeroes = se.length() - sb.length();
			for (int i = 0; i < zeroes; i++)
				bounds.append("0");
		}
		bounds.append(sb);
		bounds.append("-");
		bounds.append(se);
		if (!s.equals(BigInteger.ONE)) {
			bounds.append(":");
			bounds.append(s);
		}
		bounds.append(">");
		return bounds.toString();
	}

	/** Filters the given list of filenames according to the specified filter. */
	private String[] matchFiles(final Collection<Location> nameList,
		final NumberFilter filter)
	{
		final List<Location> list = new ArrayList<>();
		for (final Location inFile : nameList) {
			if (filter.accept(inFile.getName())) list.add(inFile);
		}
		return list.toArray(new String[list.size()]);
	}
}

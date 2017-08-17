
package io.scif.util;

import java.nio.ByteBuffer;

import org.scijava.io.handle.DataHandle;
import org.scijava.io.handle.DataHandleService;
import org.scijava.io.location.BytesLocation;
import org.scijava.io.location.Location;
import org.scijava.io.nio.ByteBufferByteBank;

public class FormatTestHelpers {

	public static DataHandle<Location> createLittleEndianHandle(
		final int capacity, final DataHandleService dataHandleService)
	{
		// little endian bytebank
		final ByteBufferByteBank buffer = new ByteBufferByteBank(cap -> {
			return ByteBuffer.allocate(cap).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		}, capacity);
		return dataHandleService.create(new BytesLocation(buffer));
	}
}

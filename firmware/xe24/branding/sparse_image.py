#!/usr/bin/env python3
"""Expand or compact Android sparse images without third-party packages."""

import argparse
import os
import struct

MAGIC = 0xED26FF3A
RAW = 0xCAC1
FILL = 0xCAC2
DONT_CARE = 0xCAC3
CRC32 = 0xCAC4
HEADER = struct.Struct("<IHHHHIIII")
CHUNK = struct.Struct("<HHII")
BLOCK_SIZE = 4096


def expand(source_path, destination_path):
    with open(source_path, "rb") as source:
        values = HEADER.unpack(source.read(HEADER.size))
        magic, major, _minor, file_header_size, chunk_header_size, block_size, total_blocks, total_chunks, _checksum = values
        if magic != MAGIC or major != 1 or file_header_size < HEADER.size or chunk_header_size < CHUNK.size:
            raise ValueError("Unsupported Android sparse image")
        source.seek(file_header_size)
        with open(destination_path, "wb") as destination:
            written_blocks = 0
            for _ in range(total_chunks):
                chunk_values = CHUNK.unpack(source.read(CHUNK.size))
                chunk_type, _reserved, chunk_blocks, total_size = chunk_values
                if chunk_header_size > CHUNK.size:
                    source.seek(chunk_header_size - CHUNK.size, os.SEEK_CUR)
                payload_size = total_size - chunk_header_size
                byte_count = chunk_blocks * block_size
                if chunk_type == RAW:
                    if payload_size != byte_count:
                        raise ValueError("Malformed raw sparse chunk")
                    copy_bytes(source, destination, byte_count)
                elif chunk_type == FILL:
                    if payload_size != 4:
                        raise ValueError("Malformed fill sparse chunk")
                    pattern = source.read(4)
                    repeated = pattern * (block_size // 4)
                    for _ in range(chunk_blocks):
                        destination.write(repeated)
                elif chunk_type == DONT_CARE:
                    if payload_size != 0:
                        raise ValueError("Malformed skip sparse chunk")
                    destination.seek(byte_count, os.SEEK_CUR)
                elif chunk_type == CRC32:
                    source.seek(payload_size, os.SEEK_CUR)
                else:
                    raise ValueError("Unknown sparse chunk type")
                written_blocks += chunk_blocks
            if written_blocks != total_blocks:
                raise ValueError("Sparse image block count mismatch")
            destination.truncate(total_blocks * block_size)


def compact(source_path, destination_path):
    size = os.path.getsize(source_path)
    if size % BLOCK_SIZE:
        raise ValueError("Raw image size is not block aligned")
    zero_block = bytes(BLOCK_SIZE)
    chunk_count = 0
    with open(source_path, "rb") as source, open(destination_path, "wb+") as destination:
        destination.write(bytes(HEADER.size))
        block = source.read(BLOCK_SIZE)
        while block:
            is_zero = block == zero_block
            blocks = [block]
            while len(blocks) < 1024:
                following = source.read(BLOCK_SIZE)
                if not following or (following == zero_block) != is_zero:
                    block = following
                    break
                blocks.append(following)
            else:
                block = source.read(BLOCK_SIZE)
            chunk_type = DONT_CARE if is_zero else RAW
            payload_size = 0 if is_zero else len(blocks) * BLOCK_SIZE
            destination.write(CHUNK.pack(chunk_type, 0, len(blocks), CHUNK.size + payload_size))
            if not is_zero:
                for item in blocks:
                    destination.write(item)
            chunk_count += 1
        destination.seek(0)
        destination.write(HEADER.pack(MAGIC, 1, 0, HEADER.size, CHUNK.size, BLOCK_SIZE,
                                      size // BLOCK_SIZE, chunk_count, 0))


def copy_bytes(source, destination, count):
    remaining = count
    while remaining:
        data = source.read(min(1024 * 1024, remaining))
        if not data:
            raise ValueError("Unexpected end of sparse image")
        destination.write(data)
        remaining -= len(data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("expand", "compact"))
    parser.add_argument("source")
    parser.add_argument("destination")
    arguments = parser.parse_args()
    (expand if arguments.operation == "expand" else compact)(arguments.source, arguments.destination)


if __name__ == "__main__":
    main()

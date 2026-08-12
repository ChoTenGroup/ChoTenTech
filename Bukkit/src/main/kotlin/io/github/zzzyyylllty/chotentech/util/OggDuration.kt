package io.github.zzzyyylllty.chotentech.util

import io.github.zzzyyylllty.chotentech.ChoTenTech
import taboolib.module.configuration.Configuration
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

object OggDuration {

    private val durationCache = ConcurrentHashMap<String, Long>()

    fun getDuration(soundName: String): Long {
        return durationCache[soundName] ?: 60_000L
    }

    fun load(config: Configuration) {
        durationCache.clear()
        val musicDir = config.getString("options.music-dir", "")
        if (musicDir.isNullOrEmpty()) {
            loadFromConfig()
            return
        }
        val dir = File(musicDir)
        if (!dir.isDirectory) {
            devLog("Music directory not found: $musicDir, fallback to config")
            loadFromConfig()
            return
        }
        val loaded = dir.listFiles { f -> f.name.endsWith(".ogg") }?.mapNotNull { file ->
            val name = file.nameWithoutExtension
            val duration = readOggDuration(file)
            if (duration > 0) {
                durationCache[name] = duration
                devLog("Music '$name': ${duration}ms")
            }
            duration
        }?.size ?: 0
        if (loaded == 0) {
            devLog("No OGG files found in $musicDir, fallback to config")
            loadFromConfig()
            return
        }
        devLog("Loaded $loaded music durations from OGG files")
    }

    private fun loadFromConfig() {
        var count = 0
        for (key in ChoTenTech.musicDurations.getKeys(false)) {
            val duration = ChoTenTech.musicDurations.getLong(key, 60_000L)
            durationCache[key] = duration
            count++
        }
        devLog("Loaded $count music durations from config")
    }

    /**
     * Parse OGG Vorbis file duration by reading the last page's granule position
     * and the sample rate from the identification header.
     */
    private fun readOggDuration(file: File): Long {
        try {
            RandomAccessFile(file, "r").use { raf ->
                // --- Read sample rate from first Vorbis identification header ---
                // OGG page: 4 bytes "OggS" magic, rest of header, then packets
                // First packet in first page = Vorbis identification header
                // In the Vorbis header at offset 12 from packet start: sample rate (4 bytes LE)
                var sampleRate = 0

                // Search for first OggS page
                raf.seek(0)
                if (raf.read() != 'O'.code || raf.read() != 'g'.code || raf.read() != 'g'.code || raf.read() != 'S'.code) {
                    return 0
                }

                // Skip: version(1), header_type(1), granule_position(8), serial(4), seq(4), crc(4), segments(1)
                raf.skipBytes(23)
                val numSegments = raf.readUnsignedByte().toInt()

                // Skip segment table
                var firstPacketSize = 0
                for (i in 0 until numSegments) {
                    val seg = raf.readUnsignedByte().toInt()
                    firstPacketSize += seg
                    if (seg < 255) break
                }

                // Read first packet = Vorbis identification header
                if (firstPacketSize < 30) return 0
                val header = ByteArray(firstPacketSize)
                raf.readFully(header)
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

                // Vorbis packet type: 1 = identification
                val packetType = buf.get()
                if (packetType != 1.toByte()) return 0

                // Vorbis magic: "vorbis" (6 bytes)
                val magic = ByteArray(6)
                buf.get(magic)
                if (String(magic) != "vorbis") return 0

                // Skip: version (4 bytes), channels (1 byte)
                buf.position(buf.position() + 5)

                // Sample rate (4 bytes LE)
                sampleRate = buf.getInt()

                if (sampleRate <= 0) return 0

                // --- Read last page's granule position ---
                // Scan backwards to find the last OggS page
                val fileLen = raf.length()
                val scanChunk = 4096L
                val startScan = (fileLen - scanChunk).coerceAtLeast(0)

                var lastGranule = 0L

                raf.seek(startScan)
                val tail = ByteArray((fileLen - startScan).toInt())
                raf.readFully(tail)

                // Find last "OggS" marker in tail
                val oggsSig = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
                var lastIndex = -1
                var searchFrom = tail.size - 4
                while (searchFrom >= 0) {
                    if (tail[searchFrom] == oggsSig[0] &&
                        tail[searchFrom + 1] == oggsSig[1] &&
                        tail[searchFrom + 2] == oggsSig[2] &&
                        tail[searchFrom + 3] == oggsSig[3]) {
                        lastIndex = searchFrom
                        break
                    }
                    searchFrom--
                }

                if (lastIndex < 0) return 0

                // granule position is at offset 6 from OggS start
                val gpBuf = ByteBuffer.wrap(tail, lastIndex + 6, 8).order(ByteOrder.LITTLE_ENDIAN)
                lastGranule = gpBuf.getLong()

                if (lastGranule <= 0) return 0

                return lastGranule * 1000 / sampleRate
            }
        } catch (_: Exception) {
            return 0
        }
    }
}

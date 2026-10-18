/*
 * Fenpo Cast: wraps Apple's ALAC decoder for the C receiver.
 *
 * This file is part of Fenpo Cast and is licensed under the GNU General Public License v3.0.
 */

#include <cstring>
#include <new>

#include "ALACBitUtilities.h"
#include "ALACDecoder.h"

#include "receiver.h"

namespace {

// The 'alac' atom AirPlay implies for ct = 2 (the same cookie UxPlay hands GStreamer):
// 352 frames per packet, 16 bit, rice parameters 40/10/14, 2 channels, 44100 Hz.
const unsigned char kMagicCookie[] = {
    0x00, 0x00, 0x00, 0x24, 0x61, 0x6c, 0x61, 0x63, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x01, 0x60, 0x00, 0x10, 0x28, 0x0a, 0x0e, 0x02, 0x00, 0xff,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xac, 0x44,
};

constexpr uint32_t kChannels = 2;

}  // namespace

extern "C" void *alac_decoder_create(void) {
    ALACDecoder *decoder = new (std::nothrow) ALACDecoder();
    if (decoder == nullptr) {
        return nullptr;
    }
    if (decoder->Init(const_cast<unsigned char *>(kMagicCookie), sizeof(kMagicCookie)) != 0) {
        delete decoder;
        return nullptr;
    }
    return decoder;
}

extern "C" int alac_decoder_decode(void *handle, const unsigned char *data, int data_len, int16_t *pcm,
                                   int max_frames) {
    ALACDecoder *decoder = static_cast<ALACDecoder *>(handle);
    if (decoder == nullptr || data == nullptr || data_len <= 0) {
        return -1;
    }
    uint32_t frame_length = decoder->mConfig.frameLength;
    if (frame_length > static_cast<uint32_t>(max_frames)) {
        return -1;
    }
    BitBuffer bits;
    BitBufferInit(&bits, const_cast<uint8_t *>(data), static_cast<uint32_t>(data_len));
    uint32_t frames = 0;
    if (decoder->Decode(&bits, reinterpret_cast<uint8_t *>(pcm), frame_length, kChannels, &frames) != 0) {
        return -1;
    }
    return static_cast<int>(frames);
}

extern "C" void alac_decoder_destroy(void *handle) {
    delete static_cast<ALACDecoder *>(handle);
}

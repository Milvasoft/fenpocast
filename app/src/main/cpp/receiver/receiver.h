/*
 * Fenpo Cast: native receiver shared declarations.
 *
 * This file is part of Fenpo Cast and is licensed under the GNU General Public License v3.0.
 */

#ifndef FENPOCAST_RECEIVER_H
#define FENPOCAST_RECEIVER_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Registers a DNS-SD service through NsdManager. Returns 0 on success. */
int receiver_register_service(const char *type, const char *name, unsigned short port,
                              const char **keys, const char **values, int count);

/* Removes a service registered with receiver_register_service. */
void receiver_unregister_service(const char *type);

/* ALAC (ct = 2) decoder for AirPlay audio: 44100 Hz, 16 bit, 2 channels, 352 frames per packet. */
void *alac_decoder_create(void);

/* Decodes one packet into interleaved 16-bit PCM. Returns the number of frames, or -1. */
int alac_decoder_decode(void *decoder, const unsigned char *data, int data_len, int16_t *pcm, int max_frames);

void alac_decoder_destroy(void *decoder);

#ifdef __cplusplus
}
#endif

#endif

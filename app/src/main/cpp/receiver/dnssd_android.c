/*
 * Fenpo Cast: dnssd back end for Android.
 *
 * UxPlay's lib/dnssd.c keeps the public dnssd_t (name, hardware address, features, pk) and
 * leaves the service registration to a back end. Upstream ships two: dns_sd (Bonjour/Avahi)
 * and mdnsd (its own responder on UDP 5353). On Android the system already runs an mDNS
 * responder, so this back end builds the same TXT records as lib/mdnsd/dnssd_mdnsd.c and hands
 * them to NsdManager through the JNI bridge (receiver.c).
 *
 * This file is part of Fenpo Cast and is licensed under the GNU General Public License v3.0.
 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "dnssd.h"
#include "dnssdint.h"
#include "utils.h"

#include "receiver.h"

#define TXT_MAX_BYTES 1024
#define TXT_MAX_ENTRIES 24
#define NAME_MAX_BYTES 256

typedef struct txt_record_s {
    unsigned char bytes[TXT_MAX_BYTES];
    int length;
    /* The same entries as key/value pairs, for NsdServiceInfo.setAttribute(). */
    char keys[TXT_MAX_ENTRIES][16];
    char values[TXT_MAX_ENTRIES][96];
    int count;
} txt_record_t;

typedef struct dnssd_private_s {
    txt_record_t raop_record;
    txt_record_t airplay_record;
    char raop_name[NAME_MAX_BYTES];
    char airplay_name[NAME_MAX_BYTES];
    int raop_registered;
    int airplay_registered;
} dnssd_private_t;

static int txt_add(txt_record_t *txt, const char *key, const char *value) {
    size_t key_len = strlen(key);
    size_t value_len = strlen(value);
    size_t entry_len = key_len + 1 + value_len;
    if (entry_len > 255 || txt->length + 1 + (int) entry_len > TXT_MAX_BYTES || txt->count >= TXT_MAX_ENTRIES ||
        key_len >= sizeof(txt->keys[0]) || value_len >= sizeof(txt->values[0])) {
        return -1;
    }
    txt->bytes[txt->length++] = (unsigned char) entry_len;
    memcpy(txt->bytes + txt->length, key, key_len);
    txt->length += (int) key_len;
    txt->bytes[txt->length++] = '=';
    memcpy(txt->bytes + txt->length, value, value_len);
    txt->length += (int) value_len;

    snprintf(txt->keys[txt->count], sizeof(txt->keys[0]), "%s", key);
    snprintf(txt->values[txt->count], sizeof(txt->values[0]), "%s", value);
    txt->count++;
    return 0;
}

static void txt_reset(txt_record_t *txt) {
    txt->length = 0;
    txt->count = 0;
}

static int build_raop_txt(dnssd_t *dnssd_public) {
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    char features[22] = {0};
    const char *pw = "false";
    const char *sf = RAOP_SF;

    switch (dnssd_public->pin_pw) {
    case 1:
        pw = "true";
        sf = "0x8c";
        break;
    case 2:
    case 3:
        pw = "true";
        sf = "0x84";
        break;
    default:
        break;
    }

    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);
    txt_record_t *txt = &dnssd->raop_record;
    txt_reset(txt);

    return txt_add(txt, "ch", RAOP_CH) ||
           txt_add(txt, "cn", RAOP_CN) ||
           txt_add(txt, "da", RAOP_DA) ||
           txt_add(txt, "et", RAOP_ET) ||
           txt_add(txt, "vv", RAOP_VV) ||
           txt_add(txt, "ft", features) ||
           txt_add(txt, "am", GLOBAL_MODEL) ||
           txt_add(txt, "md", RAOP_MD) ||
           txt_add(txt, "rhd", RAOP_RHD) ||
           txt_add(txt, "pw", pw) ||
           txt_add(txt, "sr", RAOP_SR) ||
           txt_add(txt, "ss", RAOP_SS) ||
           txt_add(txt, "sv", RAOP_SV) ||
           txt_add(txt, "tp", RAOP_TP) ||
           txt_add(txt, "txtvers", RAOP_TXTVERS) ||
           txt_add(txt, "sf", sf) ||
           txt_add(txt, "vs", RAOP_VS) ||
           txt_add(txt, "vn", RAOP_VN) ||
           txt_add(txt, "pk", dnssd_public->pk ? dnssd_public->pk : "");
}

static int build_airplay_txt(dnssd_t *dnssd_public) {
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    char device_id[3 * MAX_HWADDR_LEN];
    char features[22] = {0};

    if (utils_hwaddr_airplay(device_id, sizeof(device_id), dnssd_public->hw_addr, dnssd_public->hw_addr_len) < 0) {
        return -1;
    }

    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);
    txt_record_t *txt = &dnssd->airplay_record;
    txt_reset(txt);

    return txt_add(txt, "deviceid", device_id) ||
           txt_add(txt, "features", features) ||
           txt_add(txt, "pw", dnssd_public->pin_pw ? "true" : "false") ||
           txt_add(txt, "flags", "0x4") ||
           txt_add(txt, "model", GLOBAL_MODEL) ||
           txt_add(txt, "pk", dnssd_public->pk ? dnssd_public->pk : "") ||
           txt_add(txt, "pi", AIRPLAY_PI) ||
           txt_add(txt, "srcvers", AIRPLAY_SRCVERS) ||
           txt_add(txt, "vv", AIRPLAY_VV);
}

static int register_service(const char *type, const char *name, unsigned short port, txt_record_t *txt) {
    const char *keys[TXT_MAX_ENTRIES];
    const char *values[TXT_MAX_ENTRIES];
    for (int i = 0; i < txt->count; i++) {
        keys[i] = txt->keys[i];
        values[i] = txt->values[i];
    }
    return receiver_register_service(type, name, port, keys, values, txt->count);
}

void *dnssd_private_init(dnssd_t *dnssd_public, int *error) {
    if (error) *error = DNSSD_ERROR_NOERROR;

    dnssd_private_t *dnssd = (dnssd_private_t *) calloc(1, sizeof(dnssd_private_t));
    if (!dnssd) {
        if (error) *error = DNSSD_ERROR_OUTOFMEM;
        return NULL;
    }

    char hwaddr[2 * MAX_HWADDR_LEN + 1];
    if (utils_hwaddr_raop(hwaddr, sizeof(hwaddr), dnssd_public->hw_addr, dnssd_public->hw_addr_len) < 0) {
        free(dnssd);
        if (error) *error = DNSSD_ERROR_HWADDRLEN;
        return NULL;
    }

    /* NsdManager takes the instance name only; the service type is passed separately. */
    snprintf(dnssd->raop_name, sizeof(dnssd->raop_name), "%s@%s", hwaddr, dnssd_public->name);
    snprintf(dnssd->airplay_name, sizeof(dnssd->airplay_name), "%s", dnssd_public->name);
    return dnssd;
}

void dnssd_private_destroy(void *dnssd_private) {
    free(dnssd_private);
}

void dnssd_error_text(int *error, const char *appname) {
}

int dnssd_register_raop(dnssd_t *dnssd_public, unsigned short port) {
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (build_raop_txt(dnssd_public)) {
        return -1;
    }
    int ret = register_service("_raop._tcp", dnssd->raop_name, port, &dnssd->raop_record);
    dnssd->raop_registered = (ret == 0);
    return ret;
}

int dnssd_register_airplay(dnssd_t *dnssd_public, unsigned short port) {
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (build_airplay_txt(dnssd_public)) {
        return -1;
    }
    int ret = register_service("_airplay._tcp", dnssd->airplay_name, port, &dnssd->airplay_record);
    dnssd->airplay_registered = (ret == 0);
    return ret;
}

void dnssd_unregister_raop(dnssd_t *dnssd_public) {
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd->raop_registered) {
        receiver_unregister_service("_raop._tcp");
        dnssd->raop_registered = 0;
    }
}

void dnssd_unregister_airplay(dnssd_t *dnssd_public) {
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd->airplay_registered) {
        receiver_unregister_service("_airplay._tcp");
        dnssd->airplay_registered = 0;
    }
}

const char *dnssd_get_raop_txt(dnssd_t *dnssd_public, int *length) {
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    *length = dnssd->raop_record.length;
    return (const char *) dnssd->raop_record.bytes;
}

const char *dnssd_get_airplay_txt(dnssd_t *dnssd_public, int *length) {
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    *length = dnssd->airplay_record.length;
    return (const char *) dnssd->airplay_record.bytes;
}

int dnssd_get_service_fd(dnssd_t *dnssd_public, int service) {
    return -1;
}

int dnssd_process_service(dnssd_t *dnssd_public, int service) {
    return 0;
}

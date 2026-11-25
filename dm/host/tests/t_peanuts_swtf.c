// PEANUTS: Smoother next start's file of the transform feedback capture pipelines a game's runs made (dm/peanuts/peanuts_swtf.h, vka_pso.c sw_tf_*): an entry's check
// (a record the prewarm may take, its own hash, a layout of vka_tf_layout_read's form), the file's check (damaged entries skipped, another form or layout not used), the
// run's table (once each, at most PN_SWTF_MAX) and the save's choice (the entries the .hot file keeps: this run's first, then the runs before's)
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../../peanuts/peanuts_swtf.h"

static int fails;
#define CHECK(c) do { if (!(c)) { fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #c); fails++; } } while (0)

// A capture pipeline's entry: vertex shader vs, pixel shader fs, one buffer of two outputs (Location 0, 4 components; Location 3, 2 components: 24 bytes a vertex)
static pn_swtf_ent ent(uint64_t vs, uint64_t fs) {
    pn_swtf_ent e; memset(&e, 0, sizeof e);
    e.r.kind = VKA_PW_GFX; e.r.key[0] = vs; e.r.key[1] = fs; e.r.klen = (uint32_t)offsetof(vka_gkey, va);
    e.k.ncolor = 1; e.k.samples = 1; e.k.topo = 3;
    e.L.stride[0] = 24; e.L.n[0] = 2; e.L.loc[0][0] = 0; e.L.comp[0][0] = 4; e.L.loc[0][1] = 3; e.L.comp[0][1] = 2;
    e.h = pn_swtf_hash(&e.r, &e.k);
    return e;
}
static size_t file(uint8_t *buf, uint32_t layout, const pn_swtf_ent *e, uint32_t n) {
    pn_swtf_hdr h; memset(&h, 0, sizeof h); memcpy(h.magic, PN_SWTF_MAGIC, 8); h.n = n; h.layout = layout; h.ent_size = sizeof(pn_swtf_ent);
    memcpy(buf, &h, sizeof h); if (n) memcpy(buf + sizeof h, e, n * sizeof *e);
    return sizeof h + n * sizeof *e;
}

int main(void) {
    // an entry's hash: bit 0 set, never a list record's own (the .hot file holds both)
    pn_swtf_ent a = ent(0x1111, 0x2222), b = ent(0x3333, 0x2222), c = ent(0x5555, 0x6666);
    CHECK((a.h & 1) && a.h != b.h && a.h != (vka_pw_hash(&a.r, &a.k) | 1));
    CHECK(pn_swtf_ok(&a) && pn_swtf_ok(&b) && pn_swtf_ok(&c));

    // what may not reach the prewarm
    pn_swtf_ent x = a; x.h ^= 2; CHECK(!pn_swtf_ok(&x));                                  // a hash that is not its own
    x = a; x.r.inst[0] = 1; x.h = pn_swtf_hash(&x.r, &x.k); CHECK(!pn_swtf_ok(&x));      // instanced (a capture is drawn alone)
    x = a; x.r.kind = VKA_PW_CS; x.r.klen = 0; memset(&x.k, 0, sizeof x.k); x.h = pn_swtf_hash(&x.r, &x.k); CHECK(!pn_swtf_ok(&x));   // not a vertex pipeline
    x = a; x.k.ncolor = 200; x.h = pn_swtf_hash(&x.r, &x.k); CHECK(!pn_swtf_ok(&x));     // a key vka_pw_rec_ok refuses
    x = a; ((uint8_t *)&x.k)[sizeof x.k - 1] = 1; CHECK(!pn_swtf_ok(&x));                // bytes past klen
    x = a; x.L.stride[0] = 28; CHECK(!pn_swtf_ok(&x));                                   // a stride its outputs do not make
    x = a; x.L.comp[0][1] = 5; x.L.stride[0] = 36; CHECK(!pn_swtf_ok(&x));               // five components
    x = a; x.L.loc[0][1] = 32; CHECK(!pn_swtf_ok(&x));                                   // past the generic outputs
    x = a; x.L.n[1] = 1; CHECK(!pn_swtf_ok(&x));                                         // outputs in a buffer with no stride
    x = a; x.L.n[0] = VKA_TF_OUTS + 1; CHECK(!pn_swtf_ok(&x));
    x = a; memset(&x.L, 0, sizeof x.L); CHECK(!pn_swtf_ok(&x));                          // nothing captured

    // the file: read back; a damaged entry skipped and the others kept; another form, layout, entry size or length, or too many entries, not used
    static uint8_t buf[sizeof(pn_swtf_hdr) + (PN_SWTF_MAX + 2) * sizeof(pn_swtf_ent)];
    pn_swtf_ent three[3] = {a, b, c};
    size_t len = file(buf, 77, three, 3);
    pn_swtf_ent *got; size_t gn, skipped;
    CHECK(pn_swtf_parse(buf, len, 77, &got, &gn, &skipped) == NULL && gn == 3 && !skipped && got && got[2].h == c.h && !memcmp(&got[0].L, &a.L, sizeof a.L));
    free(got);
    three[1].L.stride[0] = 4;   // damaged
    len = file(buf, 77, three, 3);
    CHECK(pn_swtf_parse(buf, len, 77, &got, &gn, &skipped) == NULL && gn == 2 && skipped == 1 && got[0].h == a.h && got[1].h == c.h);
    free(got);
    CHECK(pn_swtf_parse(buf, len, 78, &got, &gn, &skipped) != NULL && !got && !gn);       // another list layout
    CHECK(pn_swtf_parse(buf, len - 1, 77, &got, &gn, &skipped) != NULL && !got);          // short
    CHECK(pn_swtf_parse(buf, 10, 77, &got, &gn, &skipped) != NULL && !got);
    CHECK(pn_swtf_parse(NULL, 0, 77, &got, &gn, &skipped) != NULL && !got);
    buf[0] ^= 1; CHECK(pn_swtf_parse(buf, len, 77, &got, &gn, &skipped) != NULL && !got); buf[0] ^= 1;
    { pn_swtf_hdr h; memcpy(&h, buf, sizeof h); h.ent_size += 8; memcpy(buf, &h, sizeof h); CHECK(pn_swtf_parse(buf, len, 77, &got, &gn, &skipped) != NULL && !got); }
    { static pn_swtf_ent many[PN_SWTF_MAX + 1]; for (int i = 0; i <= PN_SWTF_MAX; i++) many[i] = ent(0x100 + (uint64_t)i, 0x2222);
      len = file(buf, 77, many, PN_SWTF_MAX + 1); CHECK(pn_swtf_parse(buf, len, 77, &got, &gn, &skipped) != NULL && !got);
      len = file(buf, 77, many, PN_SWTF_MAX); CHECK(pn_swtf_parse(buf, len, 77, &got, &gn, &skipped) == NULL && gn == PN_SWTF_MAX); free(got); }
    len = file(buf, 77, NULL, 0);
    CHECK(pn_swtf_parse(buf, len, 77, &got, &gn, &skipped) == NULL && !got && !gn);

    // the run's table: each once, at most PN_SWTF_MAX
    pn_swtf_ent *run = NULL; size_t rn = 0, rcap = 0;
    CHECK(pn_swtf_add(&run, &rn, &rcap, &a) == 1 && pn_swtf_add(&run, &rn, &rcap, &a) == 0 && rn == 1);
    for (int i = 0; i < 2 * PN_SWTF_MAX; i++) { pn_swtf_ent e = ent(0x9000 + (uint64_t)i, 0x2222); pn_swtf_add(&run, &rn, &rcap, &e); }
    CHECK(rn == PN_SWTF_MAX && run[0].h == a.h);
    free(run);

    // the save: the entries the .hot file keeps, this run's first, then the runs before's, each once; one the .hot file aged out is dropped
    pn_swtf_ent mine[2] = {b, a}, before[2] = {a, c};
    pn_sw_ent keep[3]; memset(keep, 0, sizeof keep); keep[0].h = a.h; keep[1].h = b.h; keep[2].h = 0x77 | 1;
    pn_swtf_ent out[PN_SWTF_MAX];
    size_t n = pn_swtf_keep(mine, 2, before, 2, keep, 3, out, PN_SWTF_MAX);
    CHECK(n == 2 && out[0].h == b.h && out[1].h == a.h);   // c aged out
    keep[2].h = c.h;
    n = pn_swtf_keep(mine, 2, before, 2, keep, 3, out, PN_SWTF_MAX);
    CHECK(n == 3 && out[2].h == c.h);
    CHECK(pn_swtf_keep(mine, 2, before, 2, keep, 3, out, 1) == 1 && out[0].h == b.h);   // at most max
    CHECK(pn_swtf_keep(NULL, 0, before, 2, NULL, 0, out, PN_SWTF_MAX) == 0);           // learning on, nothing kept: none

    if (fails) { fprintf(stderr, "%d checks failed\n", fails); return 1; }
    printf("t_peanuts_swtf: ok\n");
    return 0;
}

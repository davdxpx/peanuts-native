"""PEANUTS(upstream-candidate): a block-compressed texture a GPU block encoder draws through an uncompressed shadow is written back into its storage on the
Android replay, as the Switch's GPU writes it there (GitHub issue #286, Tomodachi Life's face paint read back as noise). Reference: "Block-compressed textures
a GPU encoder drew, read again from their storage on the Vulkan replay" in docs/reference/rendering.md. The pieces, read from the production files: the shadow's
flush marks the level drawn, the write-back takes block-compressed formats whether the host follows the texture's storage or not, in a pool the CPU has no
access to too, and copies them in texels, a texture whose pool is not placed asks for it and waits one submission, and the bytes written reach other textures
over them. The placing and the announcing run after g_tmu is let go: pool_need takes locks that lead back to it, and the replay stopped for good when it ran
under it (on an AYN Thor, 2026-11-21)."""
from pathlib import Path
import re
import unittest

REPO = Path(__file__).resolve().parents[3]
TEX = REPO / 'dm/gfx/vk_android/vka_tex.c'
DRAW = REPO / 'dm/gfx/vk_android/vka_draw.c'
HDR = REPO / 'dm/gfx/vk_android/vka.h'


def body(source, head, end):
    start = source.index(head)
    return source[start:source.index(end, start)]


class BcWriteBack(unittest.TestCase):
    def test_the_shadow_flush_marks_the_level_drawn(self):
        flush = body(DRAW.read_text(encoding='utf-8'), 'static void bcrt_flush(void) {', '\n}\n')
        # after the shadow is copied back into the texture, the level counts as drawn, so the write-back packs it into the storage
        copy = flush.index('vkCmdCopyImage(g_cb, R.bc[k].sh->img')
        self.assertIn('vka_img_color_written(R.bc[k].im, R.bc[k].lv);', flush[copy:])

    def test_block_compressed_textures_are_written_back(self):
        kind = body(TEX.read_text(encoding='utf-8'), 'static int tiled_wb_kind(const vka_img *im) {', '\n}\n')
        self.assertIn('int bc = im->f.bw == 4 && im->f.bh == 4 && (im->f.bpb == 8 || im->f.bpb == 16);', kind)
        self.assertIn('(im->track || bc) && im->mem', kind)   # whether the host follows the storage or not
        self.assertIn('((im->f.bw == 1 && im->f.bh == 1) || bc)', kind)
        # a pool the CPU has no access to: still the storage the GPU reads again (Tomodachi Life keeps each Mii's face paint in one)
        self.assertIn('(bc ? 0 : NVN_MEMORY_POOL_FLAGS_CPU_NO_ACCESS)', kind)

    def test_levels_are_copied_in_texels(self):
        record = body(TEX.read_text(encoding='utf-8'), 'static int tiled_wb_record(', '\n}\n')
        # a buffer row of blocks holds bw texels a block; the extent is in texels, cut to the level where its last blocks overhang it
        self.assertRegex(record, re.compile(r'\(VkBufferImageCopy\)\{size, pitch / bpb \* bw, 0,'))
        self.assertIn('{ew, eh, 1}}', record)
        self.assertIn('uint32_t ew = bw == 1 || row / bpb * bw < lw ? row / bpb * bw : lw, eh = bh == 1 || rows * bh < lh ? rows * bh : lh;', record)

    def test_an_unplaced_pool_is_placed_and_the_write_back_waits_once(self):
        source = TEX.read_text(encoding='utf-8')
        record = body(source, 'static int tiled_wb_record(', '\n}\n')
        self.assertIn('if (!im->tiled_wb_retry && !vka_addr((uintptr_t)im->mem, im->L.size, NULL, NULL, NULL)) {', record)
        self.assertIn('return 2;', record)
        self.assertIn('im->tiled_wb_retry = 0;', record)   # placed: the next miss may ask again
        self.assertNotIn('mtl_pool_need(', record)   # not under g_tmu: the caller asks once it is let go
        self.assertIn('uint8_t tiled_wb_retry;', HDR.read_text(encoding='utf-8'))
        # vka_tex_writeback keeps a write-back that waits, with its reference, for the next submission, and asks for its pool after the unlock
        wb = body(source, 'void vka_tex_writeback(VkCommandBuffer cb) {', '\nint mtl_tex_gpu_owned(')
        self.assertIn('im->wb_pending = 1; g_wb[kept++] = im; continue;', wb)
        self.assertIn('g_nwb = kept;', wb)
        unlock = wb.index('pthread_mutex_unlock(&g_tmu);')
        self.assertIn('mtl_pool_need(place_at[i], place_len[i],', wb[unlock:])
        self.assertNotIn('mtl_pool_need(', wb[:unlock])

    def test_the_bytes_written_reach_other_textures(self):
        source = TEX.read_text(encoding='utf-8')
        record = body(source, 'static int tiled_wb_record(', '\n}\n')
        self.assertNotIn('vka_tex_gw_opaque(', record)   # gw_add takes g_tmu, which the caller holds here
        wb = body(source, 'void vka_tex_writeback(VkCommandBuffer cb) {', '\nint mtl_tex_gpu_owned(')
        unlock = wb.index('pthread_mutex_unlock(&g_tmu);')
        self.assertIn('vka_tex_gw_opaque(wrote_at[i], wrote_len[i]);', wb[unlock:])


if __name__ == '__main__':
    unittest.main()

package com.example.lizma.service.share;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/** The same cover is recognised across sizes and compression; another cover isn't mistaken for it. */
class CoverFingerprintTest {

    @Test
    void theSameCoverSmallerAndCompressedIsTheSamePicture() {
        for (long seed = 1; seed <= 20; seed++) {
            BufferedImage spotify = TestCovers.cover(seed);
            int distance = CoverFingerprint.of(spotify).distanceTo(CoverFingerprint.of(TestCovers.asAppleArtwork(spotify)));
            assertThat(distance).as("cover %d", seed).isLessThanOrEqualTo(SharedLinkResolver.SAME_COVER);
        }
    }

    @Test
    void anotherCoverIsNot() {
        for (long seed = 1; seed <= 20; seed++) {
            CoverFingerprint spotify = CoverFingerprint.of(TestCovers.cover(seed));
            CoverFingerprint other = CoverFingerprint.of(TestCovers.asAppleArtwork(TestCovers.cover(seed + 1000)));
            assertThat(spotify.distanceTo(other)).as("cover %d", seed).isGreaterThan(SharedLinkResolver.SAME_COVER);
        }
    }

    @Test
    void aPictureIsZeroCellsFromItself() {
        CoverFingerprint cover = CoverFingerprint.of(TestCovers.cover(7));
        assertThat(cover.distanceTo(cover)).isZero();
    }
}

package com.qeapi.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

import java.util.List;

// accepts either a single bare value or an array in JSON, but always encodes back out as an array (datagen's canonical form)
public final class FlexibleListCodec {
    private FlexibleListCodec() {}

    public static <T> Codec<List<T>> listOrSingle(Codec<T> elementCodec) {
        return Codec.either(elementCodec, elementCodec.listOf()).xmap(
                either -> either.map(List::of, list -> list),
                Either::right
        );
    }
}

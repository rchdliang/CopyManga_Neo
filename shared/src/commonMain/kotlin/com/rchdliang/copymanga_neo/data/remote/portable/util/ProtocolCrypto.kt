package com.rchdliang.copymanga_neo.data.remote.portable.util

/** Interoperability primitives for the upstream protocol, with common known-answer tests.
 * This is not a password-storage or general-purpose cryptography API. */
internal object ProtocolCrypto {
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    fun unhex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.all { it.digitToIntOrNull(16) != null }) { "无效 Hex 密文" }
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val k = if (key.size > 64) sha256(key) else key
        val inner = ByteArray(64) { ((k.getOrNull(it)?.toInt() ?: 0) xor 0x36).toByte() }
        val outer = ByteArray(64) { ((k.getOrNull(it)?.toInt() ?: 0) xor 0x5c).toByte() }
        return sha256(outer + sha256(inner + message))
    }
    private val constants = longArrayOf(
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    )
    fun sha256(message: ByteArray): ByteArray {
        val data = message.copyOf(((message.size + 9 + 63) / 64) * 64)
        data[message.size] = 0x80.toByte()
        val bits = message.size.toLong() * 8
        repeat(8) { data[data.size - 1 - it] = (bits ushr (it * 8)).toByte() }
        val h = longArrayOf(0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19).map { it.toInt() }.toIntArray()
        for (offset in data.indices step 64) {
            val w = IntArray(64)
            for (i in 0..15) for (j in 0..3) w[i] = (w[i] shl 8) or (data[offset + i * 4 + j].toInt() and 255)
            for (i in 16..63) {
                val x = w[i - 15]; val y = w[i - 2]
                w[i] = w[i - 16] + (x.rotateRight(7) xor x.rotateRight(18) xor (x ushr 3)) + w[i - 7] + (y.rotateRight(17) xor y.rotateRight(19) xor (y ushr 10))
            }
            var a=h[0];var b=h[1];var c=h[2];var d=h[3];var e=h[4];var f=h[5];var g=h[6];var z=h[7]
            repeat(64) { i ->
                val t1=z+(e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25))+((e and f) xor (e.inv() and g))+constants[i].toInt()+w[i]
                val t2=(a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22))+((a and b) xor (a and c) xor (b and c))
                z=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2
            }
            val values=intArrayOf(a,b,c,d,e,f,g,z);repeat(8){h[it]+=values[it]}
        }
        return ByteArray(32) { (h[it / 4] ushr (24 - it % 4 * 8)).toByte() }
    }
    private fun multiply(x: Int, y: Int): Int {
        var a=x;var b=y;var result=0
        repeat(8) { if (b and 1 != 0) result=result xor a; a=((a shl 1) xor (if (a and 128 != 0) 0x11b else 0)) and 255;b=b ushr 1 }
        return result
    }
    private val sbox = IntArray(256) { value ->
        var inv=if(value==0)0 else 1
        if(value!=0) repeat(254){ inv=multiply(inv,value) }
        var result=inv xor 0x63
        for (r in 1..4) result=result xor (((inv shl r) or (inv ushr (8-r))) and 255)
        result
    }
    private val inverse = IntArray(256).also { inv -> sbox.forEachIndexed { i, v -> inv[v]=i } }
    fun aesCbcPkcs7Decrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size in setOf(16,24,32) && iv.size == 16 && ciphertext.isNotEmpty() && ciphertext.size % 16 == 0) { "AES 密文参数无效" }
        val rounds=key.size/4+6
        val expanded=IntArray(16*(rounds+1));key.forEachIndexed { i,v -> expanded[i]=v.toInt() and 255 }
        var used=key.size;var rcon=1
        while(used<expanded.size) {
            val temp=IntArray(4){expanded[used-4+it]}
            if(used%key.size==0) {
                val first=temp[0];temp[0]=sbox[temp[1]] xor rcon;temp[1]=sbox[temp[2]];temp[2]=sbox[temp[3]];temp[3]=sbox[first];rcon=multiply(rcon,2)
            } else if(key.size==32 && used%key.size==16) repeat(4){temp[it]=sbox[temp[it]]}
            repeat(4){expanded[used]=expanded[used-key.size] xor temp[it];used++}
        }
        val output=ByteArray(ciphertext.size)
        var previous=iv
        for(offset in ciphertext.indices step 16) {
            val state=IntArray(16){ciphertext[offset+it].toInt() and 255}
            fun add(round:Int){repeat(16){state[it]=state[it] xor expanded[round*16+it]}}
            fun inverseShift(){val copy=state.copyOf();for(row in 0..3)for(col in 0..3)state[col*4+row]=copy[((col-row+4)%4)*4+row]}
            add(rounds)
            for(round in rounds-1 downTo 0) {
                inverseShift();repeat(16){state[it]=inverse[state[it]]};add(round)
                if(round>0) for(col in 0..3) {
                    val p=col*4;val a=state.copyOfRange(p,p+4)
                    state[p]=multiply(a[0],14) xor multiply(a[1],11) xor multiply(a[2],13) xor multiply(a[3],9)
                    state[p+1]=multiply(a[0],9) xor multiply(a[1],14) xor multiply(a[2],11) xor multiply(a[3],13)
                    state[p+2]=multiply(a[0],13) xor multiply(a[1],9) xor multiply(a[2],14) xor multiply(a[3],11)
                    state[p+3]=multiply(a[0],11) xor multiply(a[1],13) xor multiply(a[2],9) xor multiply(a[3],14)
                }
            }
            repeat(16){output[offset+it]=(state[it] xor (previous[it].toInt() and 255)).toByte()}
            previous=ciphertext.copyOfRange(offset,offset+16)
        }
        val padding=output.last().toInt() and 255
        require(padding in 1..16 && output.takeLast(padding).all { (it.toInt() and 255)==padding }) { "AES 填充无效" }
        return output.copyOf(output.size-padding)
    }
}

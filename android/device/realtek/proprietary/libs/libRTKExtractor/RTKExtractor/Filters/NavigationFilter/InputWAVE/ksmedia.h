#ifndef _WAVEFORMATEXTENSIBLE_
#define _WAVEFORMATEXTENSIBLE_

typedef struct {          // size is 16 bytes
    unsigned long   Data1;
    unsigned short	Data2;
    unsigned short	Data3;
    unsigned char   Data4[8];
} GUID;

typedef struct {
    union {
        unsigned short wValidBitsPerSample;   /* bits of precision  */
        unsigned short wSamplesPerBlock;      /* valid if wBitsPerSample==0 */
        unsigned short wReserved;             /* If neither applies, set to zero. */
    } Samples;
    
    uint32_t        dwChannelMask;      /* which channels are present in stream  */
    GUID            SubFormat;
} __attribute__((packed)) WAVEFORMATEXTENSIBLE;
/*
#if !defined( DEFINE_WAVEFORMATEX_GUID )
#define DEFINE_WAVEFORMATEX_GUID(x) (USHORT)(x), 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71
#endif

#define STATIC_KSDATAFORMAT_SUBTYPE_PCM		DEFINE_WAVEFORMATEX_GUID(WAVE_FORMAT_PCM)

//DEFINE_GUIDSTRUCT("00000001-0000-0010-8000-00aa00389b71", KSDATAFORMAT_SUBTYPE_PCM);
//#define KSDATAFORMAT_SUBTYPE_PCM DEFINE_GUIDNAMED(KSDATAFORMAT_SUBTYPE_PCM)




const ASF_GUID KSDATAFORMAT_SUBTYPE_PCM = {STATIC_KSDATAFORMAT_SUBTYPE_PCM};
//#define DEFINE_ASF_GUID(name, l, w1, w2, b1, b2, b3, b4, b5, b6, b7, b8) \
  //      const ASF_GUID name = { l, w1, w2, { b1, b2,  b3,  b4,  b5,  b6,  b7,  b8 } }


#if defined(_INC_MMREG)
#define STATIC_KSDATAFORMAT_SUBTYPE_IEEE_FLOAT\
    DEFINE_WAVEFORMATEX_GUID(WAVE_FORMAT_IEEE_FLOAT)
DEFINE_GUIDSTRUCT("00000003-0000-0010-8000-00aa00389b71", KSDATAFORMAT_SUBTYPE_IEEE_FLOAT);
#define KSDATAFORMAT_SUBTYPE_IEEE_FLOAT DEFINE_GUIDNAMED(KSDATAFORMAT_SUBTYPE_IEEE_FLOAT)

#define STATIC_KSDATAFORMAT_SUBTYPE_DRM\
    DEFINE_WAVEFORMATEX_GUID(WAVE_FORMAT_DRM)
DEFINE_GUIDSTRUCT("00000009-0000-0010-8000-00aa00389b71", KSDATAFORMAT_SUBTYPE_DRM);
#define KSDATAFORMAT_SUBTYPE_DRM DEFINE_GUIDNAMED(KSDATAFORMAT_SUBTYPE_DRM)

#define STATIC_KSDATAFORMAT_SUBTYPE_ALAW\
    DEFINE_WAVEFORMATEX_GUID(WAVE_FORMAT_ALAW)
DEFINE_GUIDSTRUCT("00000006-0000-0010-8000-00aa00389b71", KSDATAFORMAT_SUBTYPE_ALAW);
#define KSDATAFORMAT_SUBTYPE_ALAW DEFINE_GUIDNAMED(KSDATAFORMAT_SUBTYPE_ALAW)

#define STATIC_KSDATAFORMAT_SUBTYPE_MULAW\
    DEFINE_WAVEFORMATEX_GUID(WAVE_FORMAT_MULAW)
DEFINE_GUIDSTRUCT("00000007-0000-0010-8000-00aa00389b71", KSDATAFORMAT_SUBTYPE_MULAW);
#define KSDATAFORMAT_SUBTYPE_MULAW DEFINE_GUIDNAMED(KSDATAFORMAT_SUBTYPE_MULAW)

#define STATIC_KSDATAFORMAT_SUBTYPE_ADPCM\
    DEFINE_WAVEFORMATEX_GUID(WAVE_FORMAT_ADPCM)
DEFINE_GUIDSTRUCT("00000002-0000-0010-8000-00aa00389b71", KSDATAFORMAT_SUBTYPE_ADPCM);
#define KSDATAFORMAT_SUBTYPE_ADPCM DEFINE_GUIDNAMED(KSDATAFORMAT_SUBTYPE_ADPCM)

#define STATIC_KSDATAFORMAT_SUBTYPE_MPEG\
    DEFINE_WAVEFORMATEX_GUID(WAVE_FORMAT_MPEG)
DEFINE_GUIDSTRUCT("00000050-0000-0010-8000-00aa00389b71", KSDATAFORMAT_SUBTYPE_MPEG);
#define KSDATAFORMAT_SUBTYPE_MPEG DEFINE_GUIDNAMED(KSDATAFORMAT_SUBTYPE_MPEG)
#endif // defined(_INC_MMREG)

*/
#endif // !_WAVEFORMATEXTENSIBLE_

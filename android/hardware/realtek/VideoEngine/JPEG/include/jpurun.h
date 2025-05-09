//------------------------------------------------------------------------------
// File: jpurun.h
//
// Copyright (c) 2006, Chips & Media.  All rights reserved.
//------------------------------------------------------------------------------
#ifndef _JPU_RUN_H_
#define _JPU_RUN_H_



#define	MAX_FILE_PATH	256




typedef struct {
    unsigned char * buf;
    int size;
    int point;
    int count;
    int fillendbs;
} BufInfo;

typedef struct
{
    char yuvFileName[MAX_FILE_PATH];
    char bitstreamFileName[MAX_FILE_PATH];
    char huffFileName[MAX_FILE_PATH];
    char qMatFileName[MAX_FILE_PATH];
    char qpFileName[MAX_FILE_PATH];
    char cfgFileName[MAX_FILE_PATH];
    int picWidth;
    int picHeight;
    int rotAngle;
    int mirDir;
    int useRot;
    int mjpgChromaFormat;
    int outNum;
    int instNum;

    int StreamEndian;
    int FrameEndian;
    int chromaInterleave;
    int bEnStuffByte;

    // altek requirement
    int encHeaderMode;


    char strStmDir[MAX_FILE_PATH];
    char strCfgDir[MAX_FILE_PATH];
    int usePartialMode;
    int partialBufNum;
    int partialHeight;
    int packedFormat;
    int RandRotMode;
    int encQualityPercentage;
} EncConfigParam;

typedef struct
{
    char yuvFileName[MAX_FILE_PATH];
    char bitstreamFileName[MAX_FILE_PATH];
    int rotAngle;
    int mirDir;
    int useRot;
    int outNum;
    int checkeos;
    int instNum;
    int StreamEndian;
    int FrameEndian;
    int chromaInterleave;
    int iHorScaleMode;
    int iVerScaleMode;

    //ROI
    int roiEnable;
    int roiWidth;
    int roiHeight;
    int roiOffsetX;
    int roiOffsetY;
    int roiWidthInMcu;
    int roiHeightInMcu;
    int roiOffsetXInMcu;
    int roiOffsetYInMcu;

    //packed
    int packedFormat;
    int picSizeProbing;
    int picWidth;
    int picHeight;
    int picFormat;

    int usePartialMode;
    int partialBufNum;

    int partialHeight;
    int filePlay;

} DecConfigParam;


enum {
    STD_JPG_ENC
};

typedef struct {
    int codecMode;
    int numMulti;
    int saveYuv;
    int  multiMode[MAX_NUM_INSTANCE];
    char multiFileName[MAX_NUM_INSTANCE][MAX_FILE_PATH];
    char multiYuvFileName[MAX_NUM_INSTANCE][MAX_FILE_PATH];
    EncConfigParam encConfig[MAX_NUM_INSTANCE];
    DecConfigParam decConfig[MAX_NUM_INSTANCE];
} MultiConfigParam;


#if defined (__cplusplus)
extern "C" {
#endif
    int DecodeTest(DecConfigParam *param);
    int EncodeTest(EncConfigParam *param);
    int MultiInstanceTest(MultiConfigParam	*param);
#if defined (__cplusplus)
}
#endif
#endif	/* _JPU_RUN_H_ */

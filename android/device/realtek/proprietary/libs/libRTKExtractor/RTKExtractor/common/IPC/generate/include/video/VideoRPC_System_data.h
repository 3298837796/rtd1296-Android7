/*
 * Please do not edit this file.
 * It was generated using rpcgen.
 */

#ifndef _VIDEORPC_SYSTEM_DATA_H_RPCGEN
#define _VIDEORPC_SYSTEM_DATA_H_RPCGEN

#include <RPCBaseDS.h>
#include <VideoRPCBaseDS.h>

struct VIDEO_INIT_DATA {
	enum TVE_BOARD_TYPE boardType;
};
typedef struct VIDEO_INIT_DATA VIDEO_INIT_DATA;

struct VIDEO_CONFIG_DATA {
	enum VIDEO_BUFFER_TYPE videoBufferType;
};
typedef struct VIDEO_CONFIG_DATA VIDEO_CONFIG_DATA;

struct VIDEO_RPC_INSTANCE {
	enum VIDEO_VF_TYPE type;
};
typedef struct VIDEO_RPC_INSTANCE VIDEO_RPC_INSTANCE;

struct VIDEO_RPC_SET_REFCLOCK {
	long instanceID;
	long pRefClock;
};
typedef struct VIDEO_RPC_SET_REFCLOCK VIDEO_RPC_SET_REFCLOCK;

struct VIDEO_RPC_REQUEST_BUFFER {
	long width;
	long height;
};
typedef struct VIDEO_RPC_REQUEST_BUFFER VIDEO_RPC_REQUEST_BUFFER;

struct VIDEO_RPC_FILL_BUFFER {
	long width;
	long height;
	long Y_bufID;
	long C_bufID;
	struct VO_RECTANGLE fillWin;
	long lumaValue;
	long chromaValue;
};
typedef struct VIDEO_RPC_FILL_BUFFER VIDEO_RPC_FILL_BUFFER;

struct VIDEO_RPC_LOW_DELAY {
	enum VIDEO_LOW_DELAY mode;
	long reserved;
};
typedef struct VIDEO_RPC_LOW_DELAY VIDEO_RPC_LOW_DELAY;

struct VIDEO_RPC_DEC_SET_SPEED {
	long instanceID;
	long displaySpeed;
	long decodeSkip;
};
typedef struct VIDEO_RPC_DEC_SET_SPEED VIDEO_RPC_DEC_SET_SPEED;

struct VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL {
	long instanceID;
	long errConcealmentLevel;
};
typedef struct VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL;

struct VIDEO_RPC_DEC_INIT {
	long instanceID;
	VIDEO_STREAM_TYPE type;
	struct VIDEO_RPC_DEC_SET_SPEED set_speed;
};
typedef struct VIDEO_RPC_DEC_INIT VIDEO_RPC_DEC_INIT;

struct VIDEO_RPC_DEC_SET_DEBLOCK {
	long instanceID;
	u_char enable;
};
typedef struct VIDEO_RPC_DEC_SET_DEBLOCK VIDEO_RPC_DEC_SET_DEBLOCK;

struct VIDEO_RPC_DEC_BITSTREAM_BUFFER {
	long bsBase;
	long bsSize;
};
typedef struct VIDEO_RPC_DEC_BITSTREAM_BUFFER VIDEO_RPC_DEC_BITSTREAM_BUFFER;

struct VIDEO_RPC_DEC_BV_RESULT {
	long bitRate;
	long type;
};
typedef struct VIDEO_RPC_DEC_BV_RESULT VIDEO_RPC_DEC_BV_RESULT;

struct VIDEO_RPC_DEC_SEQ_INFO {
	long hor_size;
	long ver_size;
	long aspect_ratio;
	long frame_rate;
	long bit_rate;
	long vbv_buf_size;
	long profile_level;
	long chroma_format;
	long video_format;
	long disp_hor_size;
	long disp_ver_size;
	long isProg;
	long isMVC;
};
typedef struct VIDEO_RPC_DEC_SEQ_INFO VIDEO_RPC_DEC_SEQ_INFO;

struct VIDEO_MEM_CHUNK {
	long address;
	long size;
};
typedef struct VIDEO_MEM_CHUNK VIDEO_MEM_CHUNK;

struct VIDEO_RPC_VIDEO_FREE_MEMORY {
	long numMemChunks;
	struct VIDEO_MEM_CHUNK memChunk[8];
};
typedef struct VIDEO_RPC_VIDEO_FREE_MEMORY VIDEO_RPC_VIDEO_FREE_MEMORY;

struct VIDEO_RPC_CONFIG_CHUNK {
	long configMode;
	long numofChunk;
};
typedef struct VIDEO_RPC_CONFIG_CHUNK VIDEO_RPC_CONFIG_CHUNK;

struct VIDEO_RPC_DEC_CC_BYPASS_MODE {
	long instanceID;
	enum VIDEO_DECODER_CC_BYPASS_MODE cc_mode;
};
typedef struct VIDEO_RPC_DEC_CC_BYPASS_MODE VIDEO_RPC_DEC_CC_BYPASS_MODE;

struct VIDEO_RPC_DEC_SET_DNR {
	long instanceID;
	enum VIDEO_DNR_MODE dnr_mode;
};
typedef struct VIDEO_RPC_DEC_SET_DNR VIDEO_RPC_DEC_SET_DNR;

struct VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT {
	long instanceID;
	long refSyncLimit;
};
typedef struct VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT;

struct VIDEO_RPC_DEC_CAPABILITY {
	long HighWord;
	long LowWord;
};
typedef struct VIDEO_RPC_DEC_CAPABILITY VIDEO_RPC_DEC_CAPABILITY;

struct VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT {
	long instanceID;
	u_int ThumbAckAddr;
	VIDEO_COLOR_FMT colorFormat;
	u_int pTargetLuma;
	u_int pTargetChroma;
	u_int pitch;
	u_int targetRectX;
	u_int targetRectY;
	u_int targetRectWidth;
	u_int targetRectHeight;
	u_int alpha;
	u_int fillColor;
};
typedef struct VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT;

struct VIDEO_RPC_THUMBNAIL_SET_THRESHOLD {
	long instanceID;
	long threshold;
};
typedef struct VIDEO_RPC_THUMBNAIL_SET_THRESHOLD VIDEO_RPC_THUMBNAIL_SET_THRESHOLD;

struct VIDEO_RPC_FLASH_SET_OUTPUT {
	long instanceID;
	long address;
};
typedef struct VIDEO_RPC_FLASH_SET_OUTPUT VIDEO_RPC_FLASH_SET_OUTPUT;

struct VIDEO_RPC_THUMBNAIL_SET_STARTPIC {
	long instanceID;
	long startPicNum;
	long endPicNum;
};
typedef struct VIDEO_RPC_THUMBNAIL_SET_STARTPIC VIDEO_RPC_THUMBNAIL_SET_STARTPIC;

struct VIDEO_RPC_VOUT_SET_VIDEO_STANDARD {
	enum VO_STANDARD standard;
	u_char enProg;
	u_char enDIF;
	u_char enCompRGB;
	enum VO_PEDESTAL_TYPE pedType;
};
typedef struct VIDEO_RPC_VOUT_SET_VIDEO_STANDARD VIDEO_RPC_VOUT_SET_VIDEO_STANDARD;

struct VIDEO_RPC_VOUT_CONFIG_VIDEO_STANDARD {
	enum VO_STANDARD standard;
	u_char enProg;
	u_char enDIF;
	u_char enCompRGB;
	enum VO_PEDESTAL_TYPE pedType;
	u_int dataInt0;
	u_int dataInt1;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_VIDEO_STANDARD VIDEO_RPC_VOUT_CONFIG_VIDEO_STANDARD;

struct VIDEO_RPC_VOUT_SET_HDMI {
	enum VO_HDMI_MODE hdmiMode;
	enum VO_HDMI_AUDIO_SAMPLE_FREQ audioSampleFreq;
	u_char audioChannelCount;
};
typedef struct VIDEO_RPC_VOUT_SET_HDMI VIDEO_RPC_VOUT_SET_HDMI;

struct VIDEO_RPC_VOUT_CONFIG_HDMI {
	enum VO_HDMI_MODE hdmiMode;
	enum VO_HDMI_AUDIO_SAMPLE_FREQ audioSampleFreq;
	enum VO_HDMI_COLOR_FMT colorFmt;
	u_char audioChannelCount;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_HDMI VIDEO_RPC_VOUT_CONFIG_HDMI;

struct VIDEO_RPC_VOUT_CONFIG_HDMI_INFO_FRAME {
	enum VO_HDMI_MODE hdmiMode;
	enum VO_HDMI_AUDIO_SAMPLE_FREQ audioSampleFreq;
	u_char audioChannelCount;
	u_char dataByte1;
	u_char dataByte2;
	u_char dataByte3;
	u_char dataByte4;
	u_char dataByte5;
	u_int dataInt0;
	long reserved1;
	long reserved2;
	long reserved3;
	long reserved4;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_HDMI_INFO_FRAME VIDEO_RPC_VOUT_CONFIG_HDMI_INFO_FRAME;

struct VIDEO_RPC_VOUT_CONFIG_TV_SYSTEM {
	enum VO_INTERFACE_TYPE interfaceType;
	struct VIDEO_RPC_VOUT_CONFIG_VIDEO_STANDARD videoInfo;
	struct VIDEO_RPC_VOUT_CONFIG_HDMI_INFO_FRAME hdmiInfo;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_TV_SYSTEM VIDEO_RPC_VOUT_CONFIG_TV_SYSTEM;

struct VIDEO_RPC_VOUT_ANAGLYPH_CONVERSION {
	u_char enable;
	u_char switchSrcEye;
	enum VO_3D_SOURCE_FORMAT srcFormat;
};
typedef struct VIDEO_RPC_VOUT_ANAGLYPH_CONVERSION VIDEO_RPC_VOUT_ANAGLYPH_CONVERSION;

struct VIDEO_RPC_VOUT_VIDEO_ROTATE {
	u_char enable;
	enum VO_VIDEO_PLANE videoPlane;
	enum VIDEO_JPEG_ROTATION rotateAngle;
};
typedef struct VIDEO_RPC_VOUT_VIDEO_ROTATE VIDEO_RPC_VOUT_VIDEO_ROTATE;

struct VIDEO_RPC_VOUT_CONFIG_MACROVISION {
	u_char dataByte1;
	u_char dataByte2;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_MACROVISION VIDEO_RPC_VOUT_CONFIG_MACROVISION;

struct VIDEO_RPC_VOUT_SET_BKGRND {
	struct VO_COLOR bgColor;
	u_char bgEnable;
};
typedef struct VIDEO_RPC_VOUT_SET_BKGRND VIDEO_RPC_VOUT_SET_BKGRND;

struct VIDEO_RPC_VOUT_SET_MIXER_ORDER {
	u_char pic;
	u_char dd;
	u_char v1;
	u_char sub1;
	u_char v2;
	u_char osd1;
	u_char osd2;
	u_char csr;
	u_char reserved1;
	u_char reserved2;
	u_char reserved3;
	u_char reserved4;
	u_char reserved5;
};
typedef struct VIDEO_RPC_VOUT_SET_MIXER_ORDER VIDEO_RPC_VOUT_SET_MIXER_ORDER;

struct VIDEO_RPC_VOUT_CONFIGURE_PLANE_MIXER {
	long instanceID;
	enum VO_VIDEO_PLANE targetPlane;
	enum VO_VIDEO_PLANE mixOrder[8];
	struct PLANE_MIXER_WIN win[8];
	u_int dataIn0;
	u_int dataIn1;
	u_int dataIn2;
};
typedef struct VIDEO_RPC_VOUT_CONFIGURE_PLANE_MIXER VIDEO_RPC_VOUT_CONFIGURE_PLANE_MIXER;

struct VIDEO_RPC_VOUT_CONFIGURE_3D_OFFSET {
	long instanceID;
	enum VO_VIDEO_PLANE targetPlane;
	u_int delta_offset;
	u_int dataIn0;
	u_int dataIn1;
	u_int dataIn2;
};
typedef struct VIDEO_RPC_VOUT_CONFIGURE_3D_OFFSET VIDEO_RPC_VOUT_CONFIGURE_3D_OFFSET;

struct VIDEO_RPC_VOUT_SET_CC {
	u_char enCC_odd;
	u_char enCC_even;
};
typedef struct VIDEO_RPC_VOUT_SET_CC VIDEO_RPC_VOUT_SET_CC;

struct VIDEO_RPC_VOUT_SET_APS {
	u_char enExt;
	enum VO_VBI_APS APS;
};
typedef struct VIDEO_RPC_VOUT_SET_APS VIDEO_RPC_VOUT_SET_APS;

struct VIDEO_RPC_VOUT_SET_COPY_MODE {
	u_char enExt;
	enum VO_VBI_COPY_MODE copyMode;
};
typedef struct VIDEO_RPC_VOUT_SET_COPY_MODE VIDEO_RPC_VOUT_SET_COPY_MODE;

struct VIDEO_RPC_VOUT_SET_AR {
	u_char enExt;
	enum VO_VBI_ASPECT_RATIO aspectRatio;
};
typedef struct VIDEO_RPC_VOUT_SET_AR VIDEO_RPC_VOUT_SET_AR;

struct VIDEO_RPC_VOUT_CONFIG_DISP_WIN {
	enum VO_VIDEO_PLANE videoPlane;
	struct VO_RECTANGLE videoWin;
	struct VO_RECTANGLE borderWin;
	struct VO_COLOR borderColor;
	u_char enBorder;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_DISP_WIN VIDEO_RPC_VOUT_CONFIG_DISP_WIN;

struct VIDEO_RPC_VOUT_DISP_WIN_ANIMATION {
	enum VO_VIDEO_PLANE videoPlane;
	struct VO_RECTANGLE videoWinEnd;
	struct VO_RECTANGLE borderWin;
	struct VO_COLOR borderColor;
	u_char enBorder;
	u_int time;
};
typedef struct VIDEO_RPC_VOUT_DISP_WIN_ANIMATION VIDEO_RPC_VOUT_DISP_WIN_ANIMATION;

struct VIDEO_RPC_VOUT_SET_RESCALE_MODE {
	enum VO_VIDEO_PLANE videoPlane;
	enum VO_RESCALE_MODE rescaleMode;
	struct VO_RECTANGLE rescaleWindow;
	u_char delayExec;
};
typedef struct VIDEO_RPC_VOUT_SET_RESCALE_MODE VIDEO_RPC_VOUT_SET_RESCALE_MODE;

struct VIDEO_RPC_VOUT_SET_DEINT_MODE {
	enum VO_VIDEO_PLANE videoPlane;
	enum VO_DEINT_MODE deintMode;
};
typedef struct VIDEO_RPC_VOUT_SET_DEINT_MODE VIDEO_RPC_VOUT_SET_DEINT_MODE;

struct VIDEO_RPC_VOUT_ZOOM {
	enum VO_VIDEO_PLANE videoPlane;
	struct VO_RECTANGLE zoomWin;
};
typedef struct VIDEO_RPC_VOUT_ZOOM VIDEO_RPC_VOUT_ZOOM;

struct VIDEO_RPC_VOUT_PAN_ZOOM {
	enum VO_VIDEO_PLANE videoPlane;
	struct VO_RECTANGLE zoomWinStart;
	struct VO_RECTANGLE zoomWinEnd;
	u_short time;
};
typedef struct VIDEO_RPC_VOUT_PAN_ZOOM VIDEO_RPC_VOUT_PAN_ZOOM;

struct VIDEO_RPC_VOUT_TRANSPARENCY {
	enum VO_VIDEO_PLANE videoPlane;
	u_short alphaStart;
	u_short alphaEnd;
	u_short time;
};
typedef struct VIDEO_RPC_VOUT_TRANSPARENCY VIDEO_RPC_VOUT_TRANSPARENCY;

struct VIDEO_RPC_VOUT_ACTUAL_ZOOM {
	enum VO_VIDEO_PLANE videoPlane;
	enum VO_ZOOM_TYPE type;
};
typedef struct VIDEO_RPC_VOUT_ACTUAL_ZOOM VIDEO_RPC_VOUT_ACTUAL_ZOOM;

struct VIDEO_RPC_VOUT_CONFIG_OSD {
	enum VO_OSD_LPF_TYPE lpfType;
	short RGB2YUVcoeff[12];
};
typedef struct VIDEO_RPC_VOUT_CONFIG_OSD VIDEO_RPC_VOUT_CONFIG_OSD;

struct VIDEO_RPC_VOUT_CONFIG_OSD_PALETTE {
	u_char paletteIndex;
	long pPalette;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_OSD_PALETTE VIDEO_RPC_VOUT_CONFIG_OSD_PALETTE;

struct VIDEO_RPC_VOUT_CREATE_OSD_WIN {
	struct VO_RECTANGLE winPos;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	int colorKey;
	u_char alpha;
};
typedef struct VIDEO_RPC_VOUT_CREATE_OSD_WIN VIDEO_RPC_VOUT_CREATE_OSD_WIN;

struct VIDEO_RPC_VOUT_SET_OSD_WIN_PALETTE {
	u_char winID;
	u_char paletteIndex;
};
typedef struct VIDEO_RPC_VOUT_SET_OSD_WIN_PALETTE VIDEO_RPC_VOUT_SET_OSD_WIN_PALETTE;

struct VIDEO_RPC_VOUT_MODIFY_OSD_WIN {
	u_char winID;
	u_char reqMask;
	struct VO_RECTANGLE winPos;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	int colorKey;
	u_char alpha;
	u_short startX;
	u_short startY;
	u_short imgPitch;
	long pImage;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_MODIFY_OSD_WIN VIDEO_RPC_VOUT_MODIFY_OSD_WIN;

struct VIDEO_RPC_VOUT_DRAW_OSD_WIN {
	u_short winID;
	u_short startX;
	u_short startY;
	u_short imgPitch;
	long pImage;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_DRAW_OSD_WIN VIDEO_RPC_VOUT_DRAW_OSD_WIN;

struct VIDEO_RPC_VOUT_HIDE_OSD_WIN {
	u_short winID;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_HIDE_OSD_WIN VIDEO_RPC_VOUT_HIDE_OSD_WIN;

struct VIDEO_RPC_VOUT_DELETE_OSD_WIN {
	u_short winID;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_DELETE_OSD_WIN VIDEO_RPC_VOUT_DELETE_OSD_WIN;

struct VIDEO_RPC_VOUT_CONFIG_OSD_CANVAS {
	struct VO_RECTANGLE srcWin;
	struct VO_RECTANGLE dispWin;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_OSD_CANVAS VIDEO_RPC_VOUT_CONFIG_OSD_CANVAS;

struct VIDEO_RPC_VOUT_CONFIG_CURSOR {
	char alpha;
	char colorKey;
	struct VO_COLOR colorMap[4];
	enum VO_OSD_LPF_TYPE lpfType;
	long pCursorImg;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_CURSOR VIDEO_RPC_VOUT_CONFIG_CURSOR;

struct VIDEO_RPC_VOUT_CONFIG_MARS_CURSOR {
	u_short width;
	u_short height;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	u_char paletteIndex;
	long pCursorImg;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_MARS_CURSOR VIDEO_RPC_VOUT_CONFIG_MARS_CURSOR;

struct VIDEO_RPC_VOUT_DRAW_CURSOR {
	u_short x;
	u_short y;
};
typedef struct VIDEO_RPC_VOUT_DRAW_CURSOR VIDEO_RPC_VOUT_DRAW_CURSOR;

struct VIDEO_RPC_VOUT_CONFIG_COLOR_MATRIX {
	short ColorMatrixCoeff[12];
};
typedef struct VIDEO_RPC_VOUT_CONFIG_COLOR_MATRIX VIDEO_RPC_VOUT_CONFIG_COLOR_MATRIX;

struct VIDEO_RPC_VOUT_CONFIG_GRAPHIC_CANVAS {
	enum VO_GRAPHIC_PLANE plane;
	struct VO_RECTANGLE srcWin;
	struct VO_RECTANGLE dispWin;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_CONFIG_GRAPHIC_CANVAS VIDEO_RPC_VOUT_CONFIG_GRAPHIC_CANVAS;

struct VIDEO_RPC_VOUT_CREATE_GRAPHIC_WIN {
	enum VO_GRAPHIC_PLANE plane;
	struct VO_RECTANGLE winPos;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	enum VO_OSD_RGB_ORDER rgbOrder;
	int colorKey;
	u_char alpha;
	u_char reserved;
};
typedef struct VIDEO_RPC_VOUT_CREATE_GRAPHIC_WIN VIDEO_RPC_VOUT_CREATE_GRAPHIC_WIN;

struct VIDEO_RPC_VOUT_MODIFY_GRAPHIC_WIN {
	enum VO_GRAPHIC_PLANE plane;
	u_char winID;
	u_char reqMask;
	struct VO_RECTANGLE winPos;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	enum VO_OSD_RGB_ORDER rgbOrder;
	int colorKey;
	u_char alpha;
	enum VO_GRAPHIC_STORAGE_MODE storageMode;
	u_char paletteIndex;
	u_char compressed;
	u_char interlace_Frame;
	u_char bottomField;
	u_short startX[4];
	u_short startY[4];
	u_short imgPitch[4];
	long pImage[4];
	u_char reserved;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_MODIFY_GRAPHIC_WIN VIDEO_RPC_VOUT_MODIFY_GRAPHIC_WIN;

struct VIDEO_RPC_VOUT_DRAW_GRAPHIC_WIN {
	enum VO_GRAPHIC_PLANE plane;
	u_short winID;
	enum VO_GRAPHIC_STORAGE_MODE storageMode;
	u_char paletteIndex;
	u_char compressed;
	u_char interlace_Frame;
	u_char bottomField;
	u_short startX[4];
	u_short startY[4];
	u_short imgPitch[4];
	long pImage[4];
	u_char reserved;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_DRAW_GRAPHIC_WIN VIDEO_RPC_VOUT_DRAW_GRAPHIC_WIN;

struct VIDEO_RPC_VOUT_HIDE_GRAPHIC_WIN {
	enum VO_GRAPHIC_PLANE plane;
	u_short winID;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_HIDE_GRAPHIC_WIN VIDEO_RPC_VOUT_HIDE_GRAPHIC_WIN;

struct VIDEO_RPC_VOUT_DELETE_GRAPHIC_WIN {
	enum VO_GRAPHIC_PLANE plane;
	u_short winID;
	u_char go;
};
typedef struct VIDEO_RPC_VOUT_DELETE_GRAPHIC_WIN VIDEO_RPC_VOUT_DELETE_GRAPHIC_WIN;

struct VIDEO_RPC_VOUT_DISPLAY_GRAPHIC_WIN {
	long x;
	long y;
	long width;
	long height;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	enum VO_OSD_RGB_ORDER rgbOrder;
	long colorKey;
	long alpha;
	enum VO_GRAPHIC_STORAGE_MODE storageMode;
	long paletteIndex;
	long compressed;
	long interlace_Frame;
	long bottomField;
	long startX[4];
	long startY[4];
	long imgPitch[4];
	long pImage[4];
	long reserved0;
	long reserved1;
};
typedef struct VIDEO_RPC_VOUT_DISPLAY_GRAPHIC_WIN VIDEO_RPC_VOUT_DISPLAY_GRAPHIC_WIN;

struct VIDEO_RPC_VOUT_DISPLAY_GRAPHIC {
	enum VO_GRAPHIC_PLANE plane;
	u_char osdNum;
	long pGraphic;
};
typedef struct VIDEO_RPC_VOUT_DISPLAY_GRAPHIC VIDEO_RPC_VOUT_DISPLAY_GRAPHIC;

struct VIDEO_RPC_VOUT_VIDEO_CAPTURE {
	enum VO_OSD_COLOR_FORMAT colorFmt;
	long pImage;
	u_short imgPitch;
	u_short startX;
	u_short startY;
	u_short width;
	u_short height;
};
typedef struct VIDEO_RPC_VOUT_VIDEO_CAPTURE VIDEO_RPC_VOUT_VIDEO_CAPTURE;

struct VIDEO_RPC_VO_FILTER_DISPLAY {
	long instanceID;
	enum VO_VIDEO_PLANE videoPlane;
	u_char zeroBuffer;
	u_char realTimeSrc;
};
typedef struct VIDEO_RPC_VO_FILTER_DISPLAY VIDEO_RPC_VO_FILTER_DISPLAY;

struct VIDEO_RPC_VO_FILTER_HIDE {
	long instanceID;
	enum VO_VIDEO_PLANE videoPlane;
};
typedef struct VIDEO_RPC_VO_FILTER_HIDE VIDEO_RPC_VO_FILTER_HIDE;

struct VIDEO_RPC_VO_FILTER_DISP_BD_COLOR {
	long instanceID;
	enum VO_VIDEO_PLANE videoPlane;
};
typedef struct VIDEO_RPC_VO_FILTER_DISP_BD_COLOR VIDEO_RPC_VO_FILTER_DISP_BD_COLOR;

struct VIDEO_RPC_VO_FILTER_SET_SPEED {
	long instanceID;
	long speed;
};
typedef struct VIDEO_RPC_VO_FILTER_SET_SPEED VIDEO_RPC_VO_FILTER_SET_SPEED;

struct VIDEO_RPC_VO_FILTER_SHOW_STILL_PIC {
	long instanceID;
	long lumaAddr;
	long lumaPitch;
	long chromaAddr;
	long chromaPitch;
	long width;
	long height;
};
typedef struct VIDEO_RPC_VO_FILTER_SHOW_STILL_PIC VIDEO_RPC_VO_FILTER_SHOW_STILL_PIC;

struct VIDEO_RPC_VO_FILTER_FILL_VIDEO_BORDER {
	long instanceID;
	long border;
	long num_pixels;
	struct VO_COLOR fillColor;
};
typedef struct VIDEO_RPC_VO_FILTER_FILL_VIDEO_BORDER VIDEO_RPC_VO_FILTER_FILL_VIDEO_BORDER;

struct VIDEO_RPC_VO_FILTER_SET_FAST_DISPLAY {
	long instanceID;
	u_char enFastDisplay;
	u_char count;
};
typedef struct VIDEO_RPC_VO_FILTER_SET_FAST_DISPLAY VIDEO_RPC_VO_FILTER_SET_FAST_DISPLAY;

struct VIDEO_RPC_VO_FILTER_CAPTURE {
	long instanceID;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	long pImage;
	u_short imgPitch;
	u_short startX;
	u_short startY;
	u_short width;
	u_short height;
	long pStretchBuf;
	long stretchBufSize;
	long pImage_C;
	u_short imgPitch_C;
};
typedef struct VIDEO_RPC_VO_FILTER_CAPTURE VIDEO_RPC_VO_FILTER_CAPTURE;

struct VIDEO_RPC_SUBPIC_DEC_ENABLE_SUBPIC {
	long instanceID;
	u_char alwaysOnTop;
};
typedef struct VIDEO_RPC_SUBPIC_DEC_ENABLE_SUBPIC VIDEO_RPC_SUBPIC_DEC_ENABLE_SUBPIC;

struct VIDEO_RPC_SUBPIC_DEC_CONFIGURE {
	long instanceID;
	u_short fullWidth;
	u_short fullHeight;
	enum SP_STREAM_TYPE streamType;
};
typedef struct VIDEO_RPC_SUBPIC_DEC_CONFIGURE VIDEO_RPC_SUBPIC_DEC_CONFIGURE;

struct VIDEO_RPC_SUBPIC_DEC_PAGE {
	long instanceID;
	u_short page_id_composition;
	u_short page_id_ancillary;
};
typedef struct VIDEO_RPC_SUBPIC_DEC_PAGE VIDEO_RPC_SUBPIC_DEC_PAGE;

struct VIDEO_RPC_JPEG_DEC {
	long colorFormat;
	long pBitstreamRB;
	long pTargetLuma;
	long pTargetChroma;
	long pitch;
	long targetRectX;
	long targetRectY;
	long targetRectWidth;
	long targetRectHeight;
	long centerToTargetRect;
	enum VIDEO_JPEG_ROTATION rotation;
	long srcRectAlignmentRatioX;
	long srcRectAlignmentRatioY;
	long srcRectZoomFactor;
	long initOption;
	long pSharedJpegDecCtrl;
	long useWholePictureOnly;
	long ScaleUpToRect;
	long FillBlackInRect;
	long SmoothLevel;
	enum VIDEO_JPEG_MIRROR mirror;
	long pTargetLuma_right;
	long pTargetChroma_right;
	long pLumaSeq;
	long pChromaSeq;
	u_short pitchYSeq;
	u_short pitchCSeq;
	u_short widthSeq;
	u_short heightSeq;
	long reserved;
};
typedef struct VIDEO_RPC_JPEG_DEC VIDEO_RPC_JPEG_DEC;

struct VIDEO_RPC_JPEG_DEC_BATCH {
	long number;
	long structure_addr;
};
typedef struct VIDEO_RPC_JPEG_DEC_BATCH VIDEO_RPC_JPEG_DEC_BATCH;

struct VIDEO_RPC_TRANSITION_EFFECT {
	long instantID;
	long YBufID_A;
	long YBufID_B;
	long CBufID_A;
	long CBufID_B;
	long width;
	long height;
	long type;
	long frame_num;
	long PTS_inc;
	long start_x;
	long start_y;
	long start_width;
	long start_height;
	long end_x;
	long end_y;
	long end_width;
	long end_height;
	long pSharedTransEffCtrl;
	long YBufID_A_right;
	long YBufID_B_right;
	long CBufID_A_right;
	long CBufID_B_right;
};
typedef struct VIDEO_RPC_TRANSITION_EFFECT VIDEO_RPC_TRANSITION_EFFECT;

struct VIDEO_RPC_MIXER_FILTER_CONFIGURE {
	long instanceID;
	u_short width;
	u_short height;
	u_char EnablebgPicture;
	struct VO_COLOR bgColor;
	enum VO_OSD_COLOR_FORMAT colorFmt;
	enum VO_GRAPHIC_STORAGE_MODE storageMode;
	struct VO_RECTANGLE SrcWin;
	struct VO_RECTANGLE dispWin;
	u_short startX[2];
	u_short startY[2];
	u_short imgPitch[2];
	long pImage[2];
};
typedef struct VIDEO_RPC_MIXER_FILTER_CONFIGURE VIDEO_RPC_MIXER_FILTER_CONFIGURE;

struct VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN {
	long instanceID;
	long count;
	struct MIXER_WIN win[8];
};
typedef struct VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN;

struct VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN {
	long instanceID;
	u_char winID;
};
typedef struct VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN;

struct VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME {
	u_char mixerWinID;
	enum VIDEO_STREAM_TYPE stream_type;
	enum YUV_FMT yuv_fmt;
	long base;
	long size;
	long width;
	long height;
};
typedef struct VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME;

struct VIDEO_RPC_DEBUG_MEMORY {
	long videoFirmwareVersion;
};
typedef struct VIDEO_RPC_DEBUG_MEMORY VIDEO_RPC_DEBUG_MEMORY;

struct VIDEO_RPC_YUYV_TO_RGB {
	long yuyv_addr;
	long rgb_addr;
	long width;
	long height;
	long imgPitch;
	enum YUV_FMT yuv_fmt;
	enum IMG_TARGET_FORMAT rgb_fmt;
};
typedef struct VIDEO_RPC_YUYV_TO_RGB VIDEO_RPC_YUYV_TO_RGB;

#endif /* !_VIDEORPC_SYSTEM_DATA_H_RPCGEN */

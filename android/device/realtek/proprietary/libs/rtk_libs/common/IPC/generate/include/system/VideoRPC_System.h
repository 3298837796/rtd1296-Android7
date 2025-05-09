/*
 * Please do not edit this file.
 * It was generated using rpcgen.
 */

#ifndef _VIDEORPC_SYSTEM_H_RPCGEN
#define _VIDEORPC_SYSTEM_H_RPCGEN

#include <../../../include/RPCstruct.h>

#include "VideoRPC_System_data.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <RPCBaseDS.h>
#include <VideoRPCBaseDS.h>
#include <AudioRPCBaseDS.h>
#include <AudioRPC_System.h>

#define VIDEO_SYSTEM 201
#define VERSION 0

struct REG_STRUCT * VIDEO_SYSTEM_0_register(struct REG_STRUCT *rnode);

#if defined(__STDC__) || defined(__cplusplus)
#define VIDEO_RPC_COMMON_ToAgent_Create 10
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_Create_0(VIDEO_RPC_INSTANCE *, CLNT_STRUCT *);
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_Create_0_svc(VIDEO_RPC_INSTANCE *, RPC_STRUCT *, RPCRES_LONG *);
#define VIDEO_RPC_COMMON_ToAgent_Connect 20
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Connect_0(RPC_CONNECTION *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Connect_0_svc(RPC_CONNECTION *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_InitRingBuffer 30
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_InitRingBuffer_0(RPC_RINGBUFFER *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_InitRingBuffer_0_svc(RPC_RINGBUFFER *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_Run 40
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Run_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Run_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_Pause 50
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Pause_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Pause_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_Stop 60
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Stop_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Stop_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_Destroy 70
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Destroy_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Destroy_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_Flush 80
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Flush_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Flush_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_SetRefClock 90
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetRefClock_0(VIDEO_RPC_SET_REFCLOCK *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetRefClock_0_svc(VIDEO_RPC_SET_REFCLOCK *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_VideoCreate 100
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoCreate_0(VIDEO_INIT_DATA *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoCreate_0_svc(VIDEO_INIT_DATA *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_VideoConfig 105
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoConfig_0(VIDEO_CONFIG_DATA *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoConfig_0_svc(VIDEO_CONFIG_DATA *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_VideoMemoryConfig 108
extern  VIDEO_RPC_VIDEO_FREE_MEMORY * VIDEO_RPC_COMMON_ToAgent_VideoMemoryConfig_0(unsigned int *, CLNT_STRUCT *);
extern  VIDEO_RPC_VIDEO_FREE_MEMORY * VIDEO_RPC_COMMON_ToAgent_VideoMemoryConfig_0_svc(unsigned int *, RPC_STRUCT *, VIDEO_RPC_VIDEO_FREE_MEMORY *);
#define VIDEO_RPC_COMMON_ToAgent_VideoChunkConfig 109
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoChunkConfig_0(VIDEO_RPC_CONFIG_CHUNK *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoChunkConfig_0_svc(VIDEO_RPC_CONFIG_CHUNK *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_VideoDestroy 110
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoDestroy_0(VIDEO_INIT_DATA *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoDestroy_0_svc(VIDEO_INIT_DATA *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_RequestBuffer 120
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_RequestBuffer_0(VIDEO_RPC_REQUEST_BUFFER *, CLNT_STRUCT *);
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_RequestBuffer_0_svc(VIDEO_RPC_REQUEST_BUFFER *, RPC_STRUCT *, RPCRES_LONG *);
#define VIDEO_RPC_COMMON_ToAgent_ReleaseBuffer 130
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ReleaseBuffer_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ReleaseBuffer_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_ConfigLowDelay 133
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ConfigLowDelay_0(VIDEO_RPC_LOW_DELAY *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ConfigLowDelay_0_svc(VIDEO_RPC_LOW_DELAY *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_SetDebugMemory 140
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetDebugMemory_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetDebugMemory_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_VideoHalt 150
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoHalt_0(unsigned int *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoHalt_0_svc(unsigned int *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_COMMON_ToAgent_YUYV2RGB 160
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_YUYV2RGB_0(VIDEO_RPC_YUYV_TO_RGB *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_YUYV2RGB_0_svc(VIDEO_RPC_YUYV_TO_RGB *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_ToAgent_SetResourceInfo 550
extern  HRESULT * VIDEO_RPC_ToAgent_SetResourceInfo_0(VIDEO_RPC_RESOURCE_INFO *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_ToAgent_SetResourceInfo_0_svc(VIDEO_RPC_RESOURCE_INFO *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_CmprsCtrl 1005
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_CmprsCtrl_0(VIDEO_RPC_DEC_CMPRS_CTRL *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_CmprsCtrl_0_svc(VIDEO_RPC_DEC_CMPRS_CTRL *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_SetSpeed 1010
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetSpeed_0(VIDEO_RPC_DEC_SET_SPEED *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetSpeed_0_svc(VIDEO_RPC_DEC_SET_SPEED *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_SetErrorConcealmentLevel 1015
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetErrorConcealmentLevel_0(VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetErrorConcealmentLevel_0_svc(VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_Init 1020
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Init_0(VIDEO_RPC_DEC_INIT *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Init_0_svc(VIDEO_RPC_DEC_INIT *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_SetDeblock 1030
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDeblock_0(VIDEO_RPC_DEC_SET_DEBLOCK *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDeblock_0_svc(VIDEO_RPC_DEC_SET_DEBLOCK *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo 1035
extern  VIDEO_RPC_DEC_SEQ_INFO * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_0(long *, CLNT_STRUCT *);
extern  VIDEO_RPC_DEC_SEQ_INFO * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_0_svc(long *, RPC_STRUCT *, VIDEO_RPC_DEC_SEQ_INFO *);
#define VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_New 1036
extern  VIDEO_RPC_DEC_SEQ_INFO_NEW * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_New_0(long *, CLNT_STRUCT *);
extern  VIDEO_RPC_DEC_SEQ_INFO_NEW * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_New_0_svc(long *, RPC_STRUCT *, VIDEO_RPC_DEC_SEQ_INFO_NEW *);
#define VIDEO_RPC_DEC_ToAgent_BitstreamValidation 1040
extern  VIDEO_RPC_DEC_BV_RESULT * VIDEO_RPC_DEC_ToAgent_BitstreamValidation_0(VIDEO_RPC_DEC_BITSTREAM_BUFFER *, CLNT_STRUCT *);
extern  VIDEO_RPC_DEC_BV_RESULT * VIDEO_RPC_DEC_ToAgent_BitstreamValidation_0_svc(VIDEO_RPC_DEC_BITSTREAM_BUFFER *, RPC_STRUCT *, VIDEO_RPC_DEC_BV_RESULT *);
#define VIDEO_RPC_DEC_ToAgent_Capability 1045
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Capability_0(VIDEO_RPC_DEC_CAPABILITY *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Capability_0_svc(VIDEO_RPC_DEC_CAPABILITY *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_SetDecoderCCBypass 1050
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDecoderCCBypass_0(VIDEO_RPC_DEC_CC_BYPASS_MODE *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDecoderCCBypass_0_svc(VIDEO_RPC_DEC_CC_BYPASS_MODE *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_SetDNR 1060
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDNR_0(VIDEO_RPC_DEC_SET_DNR *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDNR_0_svc(VIDEO_RPC_DEC_SET_DNR *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_SetRefSyncLimit 1065
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetRefSyncLimit_0(VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetRefSyncLimit_0_svc(VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_FLASH_ToAgent_SetOutput 1085
extern  HRESULT * VIDEO_RPC_FLASH_ToAgent_SetOutput_0(VIDEO_RPC_FLASH_SET_OUTPUT *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_FLASH_ToAgent_SetOutput_0_svc(VIDEO_RPC_FLASH_SET_OUTPUT *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_THUMBNAIL_ToAgent_SetVscalerOutputFormat 1070
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetVscalerOutputFormat_0(VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetVscalerOutputFormat_0_svc(VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_THUMBNAIL_ToAgent_SetThreshold 1080
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetThreshold_0(VIDEO_RPC_THUMBNAIL_SET_THRESHOLD *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetThreshold_0_svc(VIDEO_RPC_THUMBNAIL_SET_THRESHOLD *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_VOUT_ToAgent_SetV2alpha 3090
extern  HRESULT * VIDEO_RPC_VOUT_ToAgent_SetV2alpha_0(u_char *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_VOUT_ToAgent_SetV2alpha_0_svc(u_char *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_THUMBNAIL_ToAgent_SetStartPictureNumber 1090
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetStartPictureNumber_0(VIDEO_RPC_THUMBNAIL_SET_STARTPIC *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetStartPictureNumber_0_svc(VIDEO_RPC_THUMBNAIL_SET_STARTPIC *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_DEC_ToAgent_PrivateInfo 1095
extern  VIDEO_RPC_DEC_PRIVATEINFO_OUTPUT_PARAMETERS * VIDEO_RPC_DEC_ToAgent_PrivateInfo_0(VIDEO_RPC_DEC_PRIVATEINFO_INPUT_PARAMETERS *, CLNT_STRUCT *);
extern  VIDEO_RPC_DEC_PRIVATEINFO_OUTPUT_PARAMETERS * VIDEO_RPC_DEC_ToAgent_PrivateInfo_0_svc(VIDEO_RPC_DEC_PRIVATEINFO_INPUT_PARAMETERS *, RPC_STRUCT *, VIDEO_RPC_DEC_PRIVATEINFO_OUTPUT_PARAMETERS *);
#define VIDEO_RPC_SUBPIC_DEC_ToAgent_Configure 5040
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Configure_0(VIDEO_RPC_SUBPIC_DEC_CONFIGURE *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Configure_0_svc(VIDEO_RPC_SUBPIC_DEC_CONFIGURE *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_SUBPIC_DEC_ToAgent_Page 5050
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Page_0(VIDEO_RPC_SUBPIC_DEC_PAGE *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Page_0_svc(VIDEO_RPC_SUBPIC_DEC_PAGE *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_JPEG_ToAgent_DEC 6010
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_0(VIDEO_RPC_JPEG_DEC *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_0_svc(VIDEO_RPC_JPEG_DEC *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_JPEG_ToAgent_DEC_BATCH 6011
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_BATCH_0(VIDEO_RPC_JPEG_DEC_BATCH *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_BATCH_0_svc(VIDEO_RPC_JPEG_DEC_BATCH *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_TRANSITION_ToAgent_Start 6020
extern  HRESULT * VIDEO_RPC_TRANSITION_ToAgent_Start_0(VIDEO_RPC_TRANSITION_EFFECT *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_TRANSITION_ToAgent_Start_0_svc(VIDEO_RPC_TRANSITION_EFFECT *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_MIXER_FILTER_ToAgent_Configure 8010
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_Configure_0(VIDEO_RPC_MIXER_FILTER_CONFIGURE *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_Configure_0_svc(VIDEO_RPC_MIXER_FILTER_CONFIGURE *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_MIXER_FILTER_ToAgent_ConfigureWindow 8020
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_ConfigureWindow_0(VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_ConfigureWindow_0_svc(VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_MIXER_FILTER_ToAgent_SetMasterWindow 8030
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_SetMasterWindow_0(VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_SetMasterWindow_0_svc(VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN *, RPC_STRUCT *, HRESULT *);
#define VIDEO_RPC_MIXER_ToAgent_PlayOneMotionJpegFrame 8040
extern  HRESULT * VIDEO_RPC_MIXER_ToAgent_PlayOneMotionJpegFrame_0(VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME *, CLNT_STRUCT *);
extern  HRESULT * VIDEO_RPC_MIXER_ToAgent_PlayOneMotionJpegFrame_0_svc(VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME *, RPC_STRUCT *, HRESULT *);

#else /* K&R C */
#define VIDEO_RPC_COMMON_ToAgent_Create 10
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_Create_0();
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_Create_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_Connect 20
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Connect_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Connect_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_InitRingBuffer 30
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_InitRingBuffer_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_InitRingBuffer_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_Run 40
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Run_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Run_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_Pause 50
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Pause_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Pause_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_Stop 60
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Stop_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Stop_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_Destroy 70
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Destroy_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Destroy_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_Flush 80
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Flush_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_Flush_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_SetRefClock 90
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetRefClock_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetRefClock_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_VideoCreate 100
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoCreate_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoCreate_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_VideoConfig 105
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoConfig_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoConfig_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_VideoMemoryConfig 108
extern  VIDEO_RPC_VIDEO_FREE_MEMORY * VIDEO_RPC_COMMON_ToAgent_VideoMemoryConfig_0();
extern  VIDEO_RPC_VIDEO_FREE_MEMORY * VIDEO_RPC_COMMON_ToAgent_VideoMemoryConfig_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_VideoChunkConfig 109
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoChunkConfig_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoChunkConfig_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_VideoDestroy 110
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoDestroy_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoDestroy_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_RequestBuffer 120
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_RequestBuffer_0();
extern  RPCRES_LONG * VIDEO_RPC_COMMON_ToAgent_RequestBuffer_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_ReleaseBuffer 130
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ReleaseBuffer_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ReleaseBuffer_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_ConfigLowDelay 133
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ConfigLowDelay_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_ConfigLowDelay_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_SetDebugMemory 140
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetDebugMemory_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_SetDebugMemory_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_VideoHalt 150
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoHalt_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_VideoHalt_0_svc();
#define VIDEO_RPC_COMMON_ToAgent_YUYV2RGB 160
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_YUYV2RGB_0();
extern  HRESULT * VIDEO_RPC_COMMON_ToAgent_YUYV2RGB_0_svc();
#define VIDEO_RPC_ToAgent_SetResourceInfo 550
extern  HRESULT * VIDEO_RPC_ToAgent_SetResourceInfo_0();
extern  HRESULT * VIDEO_RPC_ToAgent_SetResourceInfo_0_svc();
#define VIDEO_RPC_DEC_ToAgent_CmprsCtrl 1005
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_CmprsCtrl_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_CmprsCtrl_0_svc();
#define VIDEO_RPC_DEC_ToAgent_SetSpeed 1010
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetSpeed_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetSpeed_0_svc();
#define VIDEO_RPC_DEC_ToAgent_SetErrorConcealmentLevel 1015
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetErrorConcealmentLevel_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetErrorConcealmentLevel_0_svc();
#define VIDEO_RPC_DEC_ToAgent_Init 1020
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Init_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Init_0_svc();
#define VIDEO_RPC_DEC_ToAgent_SetDeblock 1030
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDeblock_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDeblock_0_svc();
#define VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo 1035
extern  VIDEO_RPC_DEC_SEQ_INFO * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_0();
extern  VIDEO_RPC_DEC_SEQ_INFO * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_0_svc();
#define VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_New 1036
extern  VIDEO_RPC_DEC_SEQ_INFO_NEW * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_New_0();
extern  VIDEO_RPC_DEC_SEQ_INFO_NEW * VIDEO_RPC_DEC_ToAgent_GetVideoSequenceInfo_New_0_svc();
#define VIDEO_RPC_DEC_ToAgent_BitstreamValidation 1040
extern  VIDEO_RPC_DEC_BV_RESULT * VIDEO_RPC_DEC_ToAgent_BitstreamValidation_0();
extern  VIDEO_RPC_DEC_BV_RESULT * VIDEO_RPC_DEC_ToAgent_BitstreamValidation_0_svc();
#define VIDEO_RPC_DEC_ToAgent_Capability 1045
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Capability_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_Capability_0_svc();
#define VIDEO_RPC_DEC_ToAgent_SetDecoderCCBypass 1050
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDecoderCCBypass_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDecoderCCBypass_0_svc();
#define VIDEO_RPC_DEC_ToAgent_SetDNR 1060
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDNR_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetDNR_0_svc();
#define VIDEO_RPC_DEC_ToAgent_SetRefSyncLimit 1065
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetRefSyncLimit_0();
extern  HRESULT * VIDEO_RPC_DEC_ToAgent_SetRefSyncLimit_0_svc();
#define VIDEO_RPC_FLASH_ToAgent_SetOutput 1085
extern  HRESULT * VIDEO_RPC_FLASH_ToAgent_SetOutput_0();
extern  HRESULT * VIDEO_RPC_FLASH_ToAgent_SetOutput_0_svc();
#define VIDEO_RPC_THUMBNAIL_ToAgent_SetVscalerOutputFormat 1070
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetVscalerOutputFormat_0();
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetVscalerOutputFormat_0_svc();
#define VIDEO_RPC_THUMBNAIL_ToAgent_SetThreshold 1080
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetThreshold_0();
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetThreshold_0_svc();
#define VIDEO_RPC_VOUT_ToAgent_SetV2alpha 3090
extern  HRESULT * VIDEO_RPC_VOUT_ToAgent_SetV2alpha_0();
extern  HRESULT * VIDEO_RPC_VOUT_ToAgent_SetV2alpha_0_svc();
#define VIDEO_RPC_THUMBNAIL_ToAgent_SetStartPictureNumber 1090
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetStartPictureNumber_0();
extern  HRESULT * VIDEO_RPC_THUMBNAIL_ToAgent_SetStartPictureNumber_0_svc();
#define VIDEO_RPC_DEC_ToAgent_PrivateInfo 1095
extern  VIDEO_RPC_DEC_PRIVATEINFO_OUTPUT_PARAMETERS * VIDEO_RPC_DEC_ToAgent_PrivateInfo_0();
extern  VIDEO_RPC_DEC_PRIVATEINFO_OUTPUT_PARAMETERS * VIDEO_RPC_DEC_ToAgent_PrivateInfo_0_svc();
#define VIDEO_RPC_SUBPIC_DEC_ToAgent_Configure 5040
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Configure_0();
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Configure_0_svc();
#define VIDEO_RPC_SUBPIC_DEC_ToAgent_Page 5050
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Page_0();
extern  HRESULT * VIDEO_RPC_SUBPIC_DEC_ToAgent_Page_0_svc();
#define VIDEO_RPC_JPEG_ToAgent_DEC 6010
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_0();
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_0_svc();
#define VIDEO_RPC_JPEG_ToAgent_DEC_BATCH 6011
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_BATCH_0();
extern  HRESULT * VIDEO_RPC_JPEG_ToAgent_DEC_BATCH_0_svc();
#define VIDEO_RPC_TRANSITION_ToAgent_Start 6020
extern  HRESULT * VIDEO_RPC_TRANSITION_ToAgent_Start_0();
extern  HRESULT * VIDEO_RPC_TRANSITION_ToAgent_Start_0_svc();
#define VIDEO_RPC_MIXER_FILTER_ToAgent_Configure 8010
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_Configure_0();
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_Configure_0_svc();
#define VIDEO_RPC_MIXER_FILTER_ToAgent_ConfigureWindow 8020
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_ConfigureWindow_0();
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_ConfigureWindow_0_svc();
#define VIDEO_RPC_MIXER_FILTER_ToAgent_SetMasterWindow 8030
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_SetMasterWindow_0();
extern  HRESULT * VIDEO_RPC_MIXER_FILTER_ToAgent_SetMasterWindow_0_svc();
#define VIDEO_RPC_MIXER_ToAgent_PlayOneMotionJpegFrame 8040
extern  HRESULT * VIDEO_RPC_MIXER_ToAgent_PlayOneMotionJpegFrame_0();
extern  HRESULT * VIDEO_RPC_MIXER_ToAgent_PlayOneMotionJpegFrame_0_svc();
#endif /* K&R C */

/* the xdr functions */

#if defined(__STDC__) || defined(__cplusplus)
extern  bool_t xdr_VIDEO_RPC_DEC_SET_SPEED (XDR *, VIDEO_RPC_DEC_SET_SPEED*);
extern  bool_t xdr_VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL (XDR *, VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL*);
extern  bool_t xdr_VIDEO_RPC_DEC_INIT (XDR *, VIDEO_RPC_DEC_INIT*);
extern  bool_t xdr_VIDEO_RPC_DEC_SET_DEBLOCK (XDR *, VIDEO_RPC_DEC_SET_DEBLOCK*);
extern  bool_t xdr_VIDEO_RPC_DEC_BITSTREAM_BUFFER (XDR *, VIDEO_RPC_DEC_BITSTREAM_BUFFER*);
extern  bool_t xdr_VIDEO_RPC_DEC_BV_RESULT (XDR *, VIDEO_RPC_DEC_BV_RESULT*);
extern  bool_t xdr_VIDEO_RPC_DEC_PV_RESULT (XDR *, VIDEO_RPC_DEC_PV_RESULT*);
extern  bool_t xdr_VIDEO_RPC_DEC_SEQ_INFO (XDR *, VIDEO_RPC_DEC_SEQ_INFO*);
extern  bool_t xdr_VIDEO_RPC_DEC_SEQ_INFO_NEW (XDR *, VIDEO_RPC_DEC_SEQ_INFO_NEW*);
extern  bool_t xdr_VIDEO_RPC_DEC_MPEG_SEQ_HDR (XDR *, VIDEO_RPC_DEC_MPEG_SEQ_HDR*);
extern  bool_t xdr_VIDEO_RPC_DEC_CC_BYPASS_MODE (XDR *, VIDEO_RPC_DEC_CC_BYPASS_MODE*);
extern  bool_t xdr_VIDEO_RPC_DEC_SET_DNR (XDR *, VIDEO_RPC_DEC_SET_DNR*);
extern  bool_t xdr_VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT (XDR *, VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT*);
extern  bool_t xdr_VIDEO_RPC_DEC_CAPABILITY (XDR *, VIDEO_RPC_DEC_CAPABILITY*);
extern  bool_t xdr_VIDEO_RPC_DEC_CMPRS_CTRL (XDR *, VIDEO_RPC_DEC_CMPRS_CTRL*);
extern  bool_t xdr_VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT (XDR *, VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT*);
extern  bool_t xdr_VIDEO_RPC_THUMBNAIL_SET_THRESHOLD (XDR *, VIDEO_RPC_THUMBNAIL_SET_THRESHOLD*);
extern  bool_t xdr_VIDEO_RPC_FLASH_SET_OUTPUT (XDR *, VIDEO_RPC_FLASH_SET_OUTPUT*);
extern  bool_t xdr_VIDEO_RPC_THUMBNAIL_SET_STARTPIC (XDR *, VIDEO_RPC_THUMBNAIL_SET_STARTPIC*);
extern  bool_t xdr_VIDEO_RPC_SUBPIC_DEC_CONFIGURE (XDR *, VIDEO_RPC_SUBPIC_DEC_CONFIGURE*);
extern  bool_t xdr_VIDEO_RPC_SUBPIC_DEC_PAGE (XDR *, VIDEO_RPC_SUBPIC_DEC_PAGE*);
extern  bool_t xdr_VIDEO_RPC_JPEG_DEC_BATCH (XDR *, VIDEO_RPC_JPEG_DEC_BATCH*);
extern  bool_t xdr_VIDEO_RPC_TRANSITION_EFFECT (XDR *, VIDEO_RPC_TRANSITION_EFFECT*);
extern  bool_t xdr_VIDEO_RPC_MIXER_FILTER_CONFIGURE (XDR *, VIDEO_RPC_MIXER_FILTER_CONFIGURE*);
extern  bool_t xdr_VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN (XDR *, VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN*);
extern  bool_t xdr_VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN (XDR *, VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN*);
extern  bool_t xdr_VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME (XDR *, VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME*);
extern  bool_t xdr_VIDEO_RPC_RESOURCE_INFO (XDR *, VIDEO_RPC_RESOURCE_INFO*);

#else /* K&R C */
extern bool_t xdr_VIDEO_RPC_DEC_SET_SPEED ();
extern bool_t xdr_VIDEO_RPC_DEC_SET_ERR_CONCEALMENT_LEVEL ();
extern bool_t xdr_VIDEO_RPC_DEC_INIT ();
extern bool_t xdr_VIDEO_RPC_DEC_SET_DEBLOCK ();
extern bool_t xdr_VIDEO_RPC_DEC_BITSTREAM_BUFFER ();
extern bool_t xdr_VIDEO_RPC_DEC_BV_RESULT ();
extern bool_t xdr_VIDEO_RPC_DEC_PV_RESULT ();
extern bool_t xdr_VIDEO_RPC_DEC_SEQ_INFO ();
extern bool_t xdr_VIDEO_RPC_DEC_SEQ_INFO_NEW ();
extern bool_t xdr_VIDEO_RPC_DEC_MPEG_SEQ_HDR ();
extern bool_t xdr_VIDEO_RPC_DEC_CC_BYPASS_MODE ();
extern bool_t xdr_VIDEO_RPC_DEC_SET_DNR ();
extern bool_t xdr_VIDEO_RPC_DEC_SET_REF_SYNC_LIMIT ();
extern bool_t xdr_VIDEO_RPC_DEC_CAPABILITY ();
extern bool_t xdr_VIDEO_RPC_DEC_CMPRS_CTRL ();
extern bool_t xdr_VIDEO_RPC_THUMBNAIL_SET_VSCALER_OUTFORMAT ();
extern bool_t xdr_VIDEO_RPC_THUMBNAIL_SET_THRESHOLD ();
extern bool_t xdr_VIDEO_RPC_FLASH_SET_OUTPUT ();
extern bool_t xdr_VIDEO_RPC_THUMBNAIL_SET_STARTPIC ();
extern bool_t xdr_VIDEO_RPC_SUBPIC_DEC_CONFIGURE ();
extern bool_t xdr_VIDEO_RPC_SUBPIC_DEC_PAGE ();
extern bool_t xdr_VIDEO_RPC_JPEG_DEC_BATCH ();
extern bool_t xdr_VIDEO_RPC_TRANSITION_EFFECT ();
extern bool_t xdr_VIDEO_RPC_MIXER_FILTER_CONFIGURE ();
extern bool_t xdr_VIDEO_RPC_MIXER_FILTER_CONFIGURE_WIN ();
extern bool_t xdr_VIDEO_RPC_MIXER_FILTER_SET_MASTER_WIN ();
extern bool_t xdr_VIDEO_RPC_MIXER_PLAY_ONE_MOTION_JPEG_FRAME ();
extern bool_t xdr_VIDEO_RPC_RESOURCE_INFO ();

#endif /* K&R C */

#ifdef __cplusplus
}
#endif

#endif /* !_VIDEORPC_SYSTEM_H_RPCGEN */

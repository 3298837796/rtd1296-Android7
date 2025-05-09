/**@@@+++@@@@******************************************************************
**
** Microsoft (r) PlayReady (r)
** Copyright (c) Microsoft Corporation. All rights reserved.
**
***@@@---@@@@******************************************************************
*/

#ifndef __DRMSECURECLOCK_H__
#define __DRMSECURECLOCK_H__

#include <drmliceval.h>
#include <drmsecurestore.h>
#include <drmdatastore.h>
#include <oemcryptoctx.h>
#include <drmmanagertypes.h>
#include <drmchaintypes.h>
#include <drmlicevaltypes.h>

ENTER_PK_NAMESPACE;

typedef struct __tagDRM_SECSTORE_CLOCK_DATA
{
    DRM_TID        tid;
    DRM_DWORD      fInGracePeriod; /*Used as a DRM_BOOL. DWORD for compatibility*/
    DRM_UINT64     LastKnownGoodSecureClock;
    DRM_UINT64     LastKnownRealtimeSecureClock;
    DRM_UINT64     LastKnownGracePeriodStartTime;
    DRM_UINT64     RefreshDate;
    DRM_UINT64     ChallengeTime;
    DRM_DWORD      flag; /*One bit for each member. Starting from TID. For data validity*/
} DRM_SECSTORE_CLOCK_DATA;

#define CLK_TID_VALID           0x00000001
#define CLK_IN_GP_VALID         0x00000002
#define CLK_LK_GSC_VALID        0x00000004
#define CLK_LK_RSC_VALID        0x00000008
#define CLK_LK_GP_VALID         0x00000010
#define CLK_REFRESHDATE_VALID   0x00000020
#define CLK_CHALLENGETIME_VALID 0x00000040
#define CLK_EVER_SET            0x00000080

DRM_VOID _PrepareSecureClockDataForWriting( DRM_SECSTORE_CLOCK_DATA *pData );
#define _PrepareSecureClockDataForReading _PrepareSecureClockDataForWriting

/* pseudo-hashes to read/write Data in the secure store */

extern DRM_EXPORT_VAR const DRM_ID g_idSData;
extern DRM_EXPORT_VAR const DRM_ID g_idSDataPrev;

DRM_API DRM_BOOL DRM_CALL DRM_CLK_IsSecureClockSupported(DRM_VOID);
DRM_API DRM_BOOL DRM_CALL DRM_CLK_IsSecureClockUnsupported(DRM_VOID);

/**********************************************************************
** Function:    DRM_CLK_CreateChallenge
** Synopsis:    Creates a Secure Clock challenge.
** Arguments:
**              [pdstrURL] -- pointer to DevCert struct
**              [rgbSecStoreInPswd] -- Secure store password
**              [pContext] -- pointer to DRM_CRYPTO_CONTEXT
**              [poDatastore] -- pointer to DST_CONTEXT
**              [poSecureStoreContext] -- pointer to Secure Store context
**              [pOEMContext] -- OEM implemented context
**              [pwszUrl] -- user allocated buffer to get URL in WCHAR
**              [pcchUrl] -- character count of pwszUrl. Can be NULL if URL is not required.
**              [pbData] -- user allocated buffer to be used as output buffer
**              [pcbData] -- size of pbData
** Returns:     DRM_SUCCESS on success. On Failure, it returns:
**              DRM_E_INVALIDARG
**              DRM_E_BUFFERTOOSMALL
** Notes:       To get the minimum size required, first call with pbData=NULL and
**              pcbData points to value 0. This will return DRM_E_BUFFERTOOSMALL
**              and pcbData will point to a value of minimum context size.
***********************************************************************/
DRM_API DRM_RESULT DRM_CALL DRM_CLK_CreateChallenge(
    IN const DRM_CONST_STRING        *pdstrURL,
    IN const DRM_BYTE                 rgbSecStoreInPswd [__CB_DECL(DRM_SHA1_DIGEST_LEN)],
    IN       DRM_CRYPTO_CONTEXT      *pContext,
    IN       DRM_DST                 *poDatastore,
    IN       DRM_SECSTORE_CONTEXT    *poSecureStoreContext,
    IN       DRM_VOID                *pOEMContext,
    OUT      DRM_WCHAR               *pwszUrl,
    IN OUT   DRM_DWORD               *pcchUrl,
    IN       DRM_BYTE                *pbData,
    IN OUT   DRM_DWORD               *pcbData );


/**********************************************************************
** Function:    DRM_CLK_ProcessResponse
** Synopsis:    Process Secure clock request response received from server
** Arguments:   [pDevCert] -- pointer to DevCert struct
**              [pbResponse] -- Response string received from server
**              [cbResponse] -- size of pbResponse in bytes.
**              [rgbSecStoreInPswd] -- Secure store password
**              [pResult] --pointer to DRM_RESULT to get error from server included in response
**              [pContext] --pointer to the DRM_CRYPTO_CONTEXT
**              [poDatastore] -- pointer to DST_CONTEXT
**              [poSecureStoreContext] -- pointer to Secure Store context
**              [poLicEvalContext] -- pointer to LICEVAL_CONTEXT
**              [pOEMContext] -- OEM defined context
** Returns:     DRM_SUCCESS on success, or failed with
**              DRM_E_INVALIDARG,
**              CLK_INVALID_RESPONSE, and
**              error codes from Licesne store.
**
***********************************************************************/
DRM_API DRM_RESULT DRM_CALL DRM_CLK_ProcessResponse(
    IN const PUBKEY                *ppubkeySecureClockServer,
    IN       DRM_BYTE              *pbResponse,
    IN       DRM_DWORD              cbResponse,
    IN const DRM_BYTE               rgbSecStoreInPswd [__CB_DECL(DRM_SHA1_DIGEST_LEN)],
    OUT      DRM_RESULT            *pResult,
    IN       DRM_CRYPTO_CONTEXT    *pContext,
    IN       DRM_DST               *poDatastore,
    IN       DRM_SECSTORE_CONTEXT  *poSecureStoreContext,
    IN       DRM_LICEVAL_CONTEXT   *poLicEvalContext,
    IN       DRM_VOID              *pOEMContext );

/**********************************************************************
** Function:    DRM_CLK_GetSecureClock
** Synopsis:    Gets Secure clock.
** Arguments:
**              [poSecureTime] -- pointer to WCHAR buffer to get Secure time
**              [pcchSecureTime] -- size of buffer in WCHAR
**              [pdwFlags] -- pointer to DWORD. 0 not set, 1 set, 2 need refresh
**              [pbSecureTimeWithStatus] -- pointer to BYTE buffer to get Secure time and flag status in XML format. Optional
**              [pcbSecureTimeWithStatus] -- size of buffer. Optioanl
**              [rgbSecStoreInPswd] -- Secure store password
**              [pContext] -- pointer to DRM_CRYPTO_CONTEXT
**              [poDatastore] -- pointer to DST_CONTEXT
**              [poSecureStoreContext] -- pointer to Secure Store context
**              [pOEMContext] -- OEM implmented context
** Returns:     DRM_SUCCESS on success.
***********************************************************************/
DRM_API DRM_RESULT DRM_CALL DRM_CLK_GetSecureClock(
    OUT      DRM_WCHAR            *pwszSecureTime,
    IN OUT   DRM_DWORD            *pcchSecureTime,
    OUT      DRM_DWORD            *pdwFlags,
    IN       DRM_BYTE             *pbSecureTimeWithStatus,
    IN OUT   DRM_DWORD            *pcbSecureTimeWithStatus,
    IN const DRM_BYTE              rgbSecStoreInPswd [__CB_DECL(DRM_SHA1_DIGEST_LEN)],
    IN       DRM_CRYPTO_CONTEXT   *pContext,
    IN       DRM_DST              *poDatastore,
    IN       DRM_SECSTORE_CONTEXT *poSecureStoreContext,
    IN       DRM_VOID             *pOEMContext );


/**********************************************************************
** Function:    DRM_CLK_InitCheckSecureClock
** Synopsis:    Initializes and checks Secure clock. Called by DRM_Manager_Init
** Arguments:
**              [rgbSecStoreInPswd] -- Secure store password
**              [pContext] -- pointer to DRM_CRYPTO_CONTEXT
**              [poDatastore] -- pointer to DST_CONTEXT
**              [pfClockset] -- pointer to DRM_BOOL to know whether clock is set.
**              [poSecureStoreContext] -- pointer to Secure Store context
**              [poLicEvalContext] -- pointer to LICEVAL_CONTEXT
**              [pOEMContext] -- OEM implmented context
** Returns:     DRM_SUCCESS on success.
***********************************************************************/
DRM_API DRM_RESULT DRM_CALL DRM_CLK_InitCheckSecureClock(
    IN const DRM_BYTE              rgbSecStoreInPswd [__CB_DECL(DRM_SHA1_DIGEST_LEN)],
    IN       DRM_CRYPTO_CONTEXT   *pContext,
    IN       DRM_DST              *poDatastore,
    IN OUT   DRM_BOOL             *pfClockset,
    IN       DRM_SECSTORE_CONTEXT *poSecureStoreContext,
    IN       DRM_LICEVAL_CONTEXT  *poLicEvalContext,
    IN       DRM_VOID             *pOEMContext );

DRM_API DRM_RESULT DRM_CALL DRM_CLK_CheckSecureClock(
    IN DRM_APP_CONTEXT_INTERNAL *f_poAppContextInternal );

DRM_API DRM_RESULT DRM_CALL DRM_CLK_Drm_SecureClock_ProcessResponseImpl(
    IN                          DRM_APP_CONTEXT *f_poAppContext,
    __in_bcount( f_cbResponse ) DRM_BYTE        *f_pbResponse,
    IN                          DRM_DWORD        f_cbResponse,
    OUT                         DRM_RESULT      *f_pResult );

DRM_API DRM_RESULT DRM_CALL DRM_CLK_Drm_SecureClock_GenerateChallengeImpl(
    IN                                  DRM_APP_CONTEXT *f_poAppContext,
    __out_ecount_opt( *f_pcchURL )      DRM_WCHAR       *f_pwszURL,
    IN OUT                              DRM_DWORD       *f_pcchURL,
    __out_bcount_opt( *f_pcbChallenge ) DRM_BYTE        *f_pbChallenge,
    IN OUT                              DRM_DWORD       *f_pcbChallenge );

DRM_API DRM_RESULT DRM_CALL DRM_CLK_Drm_SecureClock_GetValueImpl(
    IN                                            DRM_APP_CONTEXT *f_poAppContext,
    __in_ecount_opt( *f_pcchSecureTime )          DRM_WCHAR       *f_pwszSecureTime,
    IN OUT                                        DRM_DWORD       *f_pcchSecureTime,
    IN                                            DRM_DWORD       *f_pdwFlags,
    __in_bcount_opt( *f_pcbSecureTimeWithStatus ) DRM_BYTE        *f_pbSecureTimeWithStatus,
    IN OUT                                        DRM_DWORD       *f_pcbSecureTimeWithStatus );

/**********************************************************************
** Function:    DRM_LIC_LoadSecureTimeData
** Synopsis:    Loads secure clock data.
**              Internal to license eval code.
** Arguments:
**              [f_pFFLicense]  : Find first license structure
**              [f_pDatastore]  : Specifies the DST
** Returns:     DRM_VOID
***********************************************************************/
DRM_API DRM_VOID DRM_CALL DRM_LIC_LoadSecureTimeData(
    __inout  DRM_FFLICENSE               *f_pFFLicense,
    __in     DRM_DST                     *f_pDatastore );

/**********************************************************************
** Function:    DRM_LIC_CheckGracePeriod
** Synopsis:    Verifies that a license referencing the clock does
**              not successfully evaluate while in the grace period.
**              Internal to license eval code.
** Arguments:
**              [f_pFFLicense]  : Find first license structure
** Returns:     DRM_SUCCESS on success.
**              DRM_E_RIGHTSNOTAVAILABLE on failure.
***********************************************************************/
DRM_API DRM_RESULT DRM_CALL DRM_LIC_CheckGracePeriod(
    __in     DRM_FFLICENSE               *f_pFFLicense );

/**********************************************************************
** Function:    DRM_LEVL_CheckGracePeriodXMR
** Synopsis:    Verifies that an XMR license referencing the clock does
**              not successfully evaluate while in the grace period.
**              Internal to license eval code.
** Arguments:
**              [f_pContextLicEval]         : License eval structure
**              [f_ui64CurrentDateTime]     : Current date time
**              [f_pXMR]                    : XMR license
**              [f_pfGracePeriodExceeded]   : Set to TRUE if grace period exceeded
** Returns:     DRM_SUCCESS on success.
**              DRM_E_RIGHTSNOTAVAILABLE on failure.
***********************************************************************/
DRM_API DRM_RESULT DRM_CALL DRM_LEVL_CheckGracePeriodXMR(
    __inout     DRM_LICEVAL_CONTEXT          *f_pContextLicEval,
    __in        DRM_UINT64                    f_ui64CurrentDateTime,
    __in        DRM_XMR_LICENSE              *f_pXMR,
    __out       DRM_BOOL                     *f_pfGracePeriodExceeded );

DRM_API DRM_RESULT DRM_CALL DRM_CLK_VariableSecureTimeGetorSet(
    __in    const DRM_CONST_STRING  *pdstrToken,
    __in          TOKEN             *pNewValue,
    __inout       TOKEN             *pResult,
    __inout       DRM_VOID          *pvOpaqueData,
    __in          DRM_BOOL           fSet );

EXIT_PK_NAMESPACE;

#endif /*__DRMSECURECLOCK_H__ */


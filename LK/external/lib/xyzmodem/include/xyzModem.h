/*
 *==========================================================================
 *
 *      xyzModem.h
 *
 *      RedBoot stream handler for xyzModem protocol
 *
 *==========================================================================
 * SPDX-License-Identifier:	eCos-2.0
 *==========================================================================
 *#####DESCRIPTIONBEGIN####
 *
 * Author(s):    gthomas
 * Contributors: gthomas
 * Date:         2000-07-14
 * Purpose:
 * Description:
 *
 * This code is part of RedBoot (tm).
 *
 *####DESCRIPTIONEND####
 *
 *==========================================================================
 */

#ifndef _XYZMODEM_H_
#define _XYZMODEM_H_

#include <stdbool.h>
#include <debug.h>

#define xyzModem_xmodem 1
#define xyzModem_ymodem 2
/* Don't define this until the protocol support is in place */
/*#define xyzModem_zmodem 3 */

#define xyzModem_access   -1
#define xyzModem_noZmodem -2
#define xyzModem_timeout  -3
#define xyzModem_eof      -4
#define xyzModem_cancel   -5
#define xyzModem_frame    -6
#define xyzModem_cksum    -7
#define xyzModem_sequence -8

#define xyzModem_close 1
#define xyzModem_abort 2


#ifdef REDBOOT
extern getc_io_funcs_t xyzModem_io;
#else
#define CYGNUM_CALL_IF_SET_COMM_ID_QUERY_CURRENT
#define CYGACC_CALL_IF_SET_CONSOLE_COMM(x)

#define diag_vprintf vprintf
#define diag_printf printf
#define diag_vsprintf vsprintf

#define CYGACC_CALL_IF_DELAY_US(x) spin(x)

typedef struct {
    char *filename;
    int   mode;
    int   chan;
#ifdef CYGPKG_REDBOOT_NETWORKING
    struct sockaddr_in *server;
#endif
} connection_info_t;

#endif


int   xyzModem_stream_open(connection_info_t *info, int *err);
void  xyzModem_stream_close(int *err);
void  xyzModem_stream_terminate(bool method, int (*getc)(void));
int   xyzModem_stream_read(char *buf, int size, int *err);
const char *xyzModem_error(int err);

#endif /* _XYZMODEM_H_ */

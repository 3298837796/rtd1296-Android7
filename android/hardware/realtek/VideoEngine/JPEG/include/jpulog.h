//------------------------------------------------------------------------------
// File: log.h
//
// Copyright (c) 2006, Chips & Media.  All rights reserved.
//------------------------------------------------------------------------------

#ifndef _LOG_H_
#define _LOG_H_



enum {NONE=0, INFO, WARN, ERR, TRACE, MAX_LOG_LEVEL};
enum
{
    LOG_HAS_DAY_NAME   =    1, /**< Include day name [default: no] 	      */
    LOG_HAS_YEAR       =    2, /**< Include year digit [no]		      */
    LOG_HAS_MONTH	  =    4, /**< Include month [no]		      */
    LOG_HAS_DAY_OF_MON =    8, /**< Include day of month [no]	      */
    LOG_HAS_TIME	  =   16, /**< Include time [yes]		      */
    LOG_HAS_MICRO_SEC  =   32, /**< Include microseconds [yes]             */
    LOG_HAS_FILE	  =   64, /**< Include sender in the log [yes] 	      */
    LOG_HAS_NEWLINE	  =  128, /**< Terminate each call with newline [yes] */
    LOG_HAS_CR	  =  256, /**< Include carriage return [no] 	      */
    LOG_HAS_SPACE	  =  512, /**< Include two spaces before log [yes]    */
    LOG_HAS_COLOR	  = 1024, /**< Colorize logs [yes on win32]	      */
    LOG_HAS_LEVEL_TEXT = 2048 /**< Include level text string [no]	      */
};
enum {
    TERM_COLOR_R	= 2,    /**< Red            */
    TERM_COLOR_G	= 4,    /**< Green          */
    TERM_COLOR_B	= 1,    /**< Blue.          */
    TERM_COLOR_BRIGHT = 8    /**< Bright mask.   */
};

#define MAX_PRINT_LENGTH 512
#define JLOG LogMsgJpu



#define LOG_ENABLE_FILE	SetLogDecor(GetLogDecor()|LOG_HAS_FILE);
#if defined (__cplusplus)
extern "C" {
#endif

    int InitLog();
    void DeInitLog();

    void SetMaxLogLevel(int level);
    int GetMaxLogLevel();

    void SetLogColor(int level, int color);
    int GetLogColor(int level);

    void SetLogDecor(int decor);
    int GetLogDecor();

    void LogMsgJpu(int level, const char *format, ...);

#if defined(linux) || defined(__linux) || defined(ANDROID)
    int kbhit();
    int getch();
    void init_keyboard();
    void close_keyboard();
#endif

    void timer_start();
    void timer_stop();
    double timer_elapsed_us();
    double timer_elapsed_ms();
    int timer_is_valid();
    double timer_frequency();


#if defined (__cplusplus)
}
#endif

#endif //#ifndef _LOG_H_

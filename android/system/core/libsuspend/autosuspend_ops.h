/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _LIBSUSPEND_AUTOSUSPEND_OPS_H_
#define _LIBSUSPEND_AUTOSUSPEND_OPS_H_

struct autosuspend_ops {
    int (*enable)(void);
    int (*disable)(void);
};

struct autosuspend_ops *autosuspend_autosleep_init(void);
struct autosuspend_ops *autosuspend_earlysuspend_init(void);
struct autosuspend_ops *autosuspend_wakeup_count_init(void);
struct autosuspend_ops *autosuspend_rtk_init(void);

#endif

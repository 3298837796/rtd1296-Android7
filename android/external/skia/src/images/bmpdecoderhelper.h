
/*
 * Copyright 2007 The Android Open Source Project
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */


#ifndef IMAGE_CODEC_BMPDECODERHELPER_H__
#define IMAGE_CODEC_BMPDECODERHELPER_H__

///////////////////////////////////////////////////////////////////////////////
// this section is my current "glue" between google3 code and android.
// will be fixed soon

#include "SkTypes.h"
#include "SkStream.h"
#include <limits.h>
#define DISALLOW_EVIL_CONSTRUCTORS(name)
#define CHECK(predicate)  SkASSERT(predicate)
typedef uint8_t uint8;
typedef uint32_t uint32;

template <typename T> class scoped_array {
private:
  T* ptr_;
  scoped_array(scoped_array const&);
  scoped_array& operator=(const scoped_array&);

public:
  explicit scoped_array(T* p = 0) : ptr_(p) {}
  ~scoped_array() {
    delete[] ptr_;
  }

  void reset(T* p = 0) {
    if (p != ptr_) {
      delete[] ptr_;
      ptr_ = p;
    }
  }

  T& operator[](int i) const {
    return ptr_[i];
  }
};

///////////////////////////////////////////////////////////////////////////////

namespace image_codec {

class BmpDecoderCallback {
 public:
  BmpDecoderCallback() { }
  virtual ~BmpDecoderCallback() {}

  /**
   * This is called once for an image. It is passed the width and height and
   * should return the address of a buffer that is large enough to store
   * all of the resulting pixels (widht * height * 3 bytes). If it returns nullptr,
   * then the decoder will abort, but return true, as the caller has received
   * valid dimensions.
   */
  virtual uint8* SetSize(int width, int height) = 0;
  virtual bool Exceed() = 0;

 private:
  DISALLOW_EVIL_CONSTRUCTORS(BmpDecoderCallback);
};

class BmpDecoderHelper {
 public:
  BmpDecoderHelper() { }
  ~BmpDecoderHelper() { }
  bool DecodeImage(SkStream* stream,
                   int len,
                   int max_pixels,
                   BmpDecoderCallback* callback);
  const uint8_t* GetRow(int row);

 private:
  DISALLOW_EVIL_CONSTRUCTORS(BmpDecoderHelper);

  void DoRLEDecode();
  void DoStandardDecode();
  void DoStandardDecode_24();
  void DoRLEDecode(int row);
  void DoStandardDecode(int row);
  void DoStandardDecode_24(int row);
  void SwapRow(char *_src, char *_dst, int _size);
  void PutPixel(int x, int y, uint8 col);  

  int GetInt();
  int GetShort();
  uint8 GetByte();
  int CalcShiftRight(uint32 mask);
  int CalcShiftLeft(uint32 mask);

  SkStream* data_;
  int imgDataBase_;
  int pos_;
  int len_;
  int width_;
  int height_;
  int bpp_;
  int pixelPad_;
  int rowPad_;
  scoped_array<uint8> colTab_;
  uint32 redBits_;
  uint32 greenBits_;
  uint32 blueBits_;
  int redShiftRight_;
  int greenShiftRight_;
  int blueShiftRight_;
  int redShiftLeft_;
  int greenShiftLeft_;
  int blueShiftLeft_;
  uint8* output_;
  bool inverted_;
  bool rle_;
};

} // namespace

#endif

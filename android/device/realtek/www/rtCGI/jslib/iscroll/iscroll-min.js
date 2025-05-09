(function () {
    function l(p, n) {
        var q = this,
            o;
        q.element = typeof p == "object" ? p : document.getElementById(p);
        console.log(p);
        q.wrapper = q.element.parentNode;        
        q.element.style.webkitTransitionProperty = "-webkit-transform";
        q.element.style.webkitTransitionTimingFunction = "cubic-bezier(0,0,0.25,1)";
        q.element.style.webkitTransitionDuration = "0";
        q.element.style.webkitTransform = j + "0,0" + b;
        q.options = {
            bounce: f,
            momentum: f,
            checkDOMChanges: true,
            topOnDOMChanges: false,
            hScrollbar: f,
            vScrollbar: f,
            fadeScrollbar: c || d || !a,
            shrinkScrollbar: c || d || !a,
            desktopCompatibility: false,
            overflow: "hidden",
            snap: false
        };
        if (typeof n == "object") {
            for (o in n) {
                q.options[o] = n[o]
            }
        }
        if (q.options.desktopCompatibility) {
            q.options.overflow = "hidden"
        }
        q.wrapper.style.overflow = q.options.overflow;
        setTimeout(function () {
            q.refresh();
        }, 50);
        window.addEventListener("onorientationchange" in window ? "orientationchange" : "resize", q, false);
        if (a || q.options.desktopCompatibility) {
            q.element.addEventListener(h, q, false);
            q.element.addEventListener(k, q, false);
            q.element.addEventListener(g, q, false)
        }
        if (q.options.checkDOMChanges) {
            q.element.addEventListener("DOMSubtreeModified", q, false)
        }
    }
    l.prototype = {
        x: 0,
        y: 0,
        enabled: true,
        handleEvent: function (o) {
            var n = this;
            switch (o.type) {
            case h:
                n.touchStart(o);
                break;
            case k:
                n.touchMove(o);
                break;
            case g:
                n.touchEnd(o);
                break;
            case "webkitTransitionEnd":
                n.transitionEnd();
                break;
            case "orientationchange":
            case "resize":
                n.refresh();
                break;
            case "DOMSubtreeModified":
                n.onDOMModified(o);
                break
            }
        },
        onDOMModified: function (o) {
            var n = this;
            if (o.target.parentNode != n.element) {
                return
            }
            setTimeout(function () {
                n.refresh()
            }, 0);
            if (n.options.topOnDOMChanges && (n.x != 0 || n.y != 0)) {
                n.scrollTo(0, 0, "0")
            }
        },
        refresh: function () {
            var o = this,
                q = this.x,
                p = this.y,
                n;
            o.scrollWidth = o.wrapper.clientWidth;
            o.scrollHeight = o.wrapper.clientHeight;
            o.scrollerWidth = o.element.offsetWidth;
            o.scrollerHeight = o.element.offsetHeight;
            o.maxScrollX = o.scrollWidth - o.scrollerWidth;
            o.maxScrollY = o.scrollHeight - o.scrollerHeight;
            o.directionX = 0;
            o.directionY = 0;
            if (o.scrollX) {
                if (o.maxScrollX >= 0) {
                    q = 0
                } else {
                    if (o.x < o.maxScrollX) {
                        q = o.maxScrollX
                    }
                }
            }
            if (o.scrollY) {
                if (o.maxScrollY >= 0) {
                    p = 0
                } else {
                    if (o.y < o.maxScrollY) {
                        p = o.maxScrollY
                    }
                }
            }
            if (o.options.snap) {
                o.maxPageX = -Math.floor(o.maxScrollX / o.scrollWidth);
                o.maxPageY = -Math.floor(o.maxScrollY / o.scrollHeight);
                n = o.snap(q, p);
                q = n.x;
                p = n.y
            }
            if (q != o.x || p != o.y) {
                o.setTransitionTime("0");
                o.setPosition(q, p, true)
            }
            o.scrollX = o.scrollerWidth > o.scrollWidth;
            o.scrollY = !o.scrollX || o.scrollerHeight > o.scrollHeight;
            if (o.options.hScrollbar && o.scrollX) {
                o.scrollBarX = o.scrollBarX || new m("horizontal", o.wrapper, o.options.fadeScrollbar, o.options.shrinkScrollbar);
                o.scrollBarX.init(o.scrollWidth, o.scrollerWidth)
            } else {
                if (o.scrollBarX) {
                    o.scrollBarX = o.scrollBarX.remove()
                }
            }
            if (o.options.vScrollbar && o.scrollY && o.scrollerHeight > o.scrollHeight) {
                o.scrollBarY = o.scrollBarY || new m("vertical", o.wrapper, o.options.fadeScrollbar, o.options.shrinkScrollbar);
                o.scrollBarY.init(o.scrollHeight, o.scrollerHeight)
            } else {
                if (o.scrollBarY) {
                    o.scrollBarY = o.scrollBarY.remove()
                }
            }
        },
        setPosition: function (n, q, p) {
            var o = this;
            o.x = n;
            o.y = q;
            o.element.style.webkitTransform = j + o.x + "px," + o.y + "px" + b;
            if (!p) {
                if (o.scrollBarX) {
                    o.scrollBarX.setPosition(o.x)
                }
                if (o.scrollBarY) {
                    o.scrollBarY.setPosition(o.y)
                }
            }
        },
        setTransitionTime: function (o) {
            var n = this;
            o = o || "0";
            n.element.style.webkitTransitionDuration = o;
            if (n.scrollBarX) {
                n.scrollBarX.bar.style.webkitTransitionDuration = o;
                n.scrollBarX.wrapper.style.webkitTransitionDuration = f && n.options.fadeScrollbar ? "300ms" : "0"
            }
            if (n.scrollBarY) {
                n.scrollBarY.bar.style.webkitTransitionDuration = o;
                n.scrollBarY.wrapper.style.webkitTransitionDuration = f && n.options.fadeScrollbar ? "300ms" : "0"
            }
        },
        touchStart: function (p) {
            var o = this,
                n;
            p.preventDefault();
            p.stopPropagation();
            if (!o.enabled) {
                return
            }
            o.scrolling = true;
            o.moved = false;
            o.dist = 0;
            o.setTransitionTime("0");
            if (o.options.momentum || o.options.snap) {
                n = new WebKitCSSMatrix(window.getComputedStyle(o.element).webkitTransform);
                if (n.e != o.x || n.f != o.y) {
                    document.removeEventListener("webkitTransitionEnd", o, false);
                    o.setPosition(n.e, n.f);
                    o.moved = true
                }
            }
            o.touchStartX = a ? p.changedTouches[0].pageX : p.pageX;
            o.scrollStartX = o.x;
            o.touchStartY = a ? p.changedTouches[0].pageY : p.pageY;
            o.scrollStartY = o.y;
            o.scrollStartTime = p.timeStamp;
            o.directionX = 0;
            o.directionY = 0
        },
        touchMove: function (t) {
            var r = this,
                q = a ? t.changedTouches[0].pageX : t.pageX,
                p = a ? t.changedTouches[0].pageY : t.pageY,
                o = r.scrollX ? q - r.touchStartX : 0,
                n = r.scrollY ? p - r.touchStartY : 0,
                u = r.x + o,
                s = r.y + n;
            if (!r.scrolling) {
                return
            }
            t.stopPropagation();
            r.touchStartX = q;
            r.touchStartY = p;
            if (u >= 0 || u < r.maxScrollX) {
                u = r.options.bounce ? Math.round(r.x + o / 3) : (u >= 0 || r.maxScrollX >= 0) ? 0 : r.maxScrollX
            }
            if (s >= 0 || s < r.maxScrollY) {
                s = r.options.bounce ? Math.round(r.y + n / 3) : (s >= 0 || r.maxScrollY >= 0) ? 0 : r.maxScrollY
            }
            if (r.dist > 5) {
                r.setPosition(u, s);
                r.moved = true;
                r.directionX = o > 0 ? -1 : 1;
                r.directionY = n > 0 ? -1 : 1
            } else {
                r.dist += Math.abs(o) + Math.abs(n)
            }
        },
        touchEnd: function (v) {
            var u = this,
                q = v.timeStamp - u.scrollStartTime,
                y = a ? v.changedTouches[0] : v,
                w, x, p, n, o = 0,
                t = u.x,
                s = u.y,
                r;
            if (!u.scrolling) {
                return
            }
            u.scrolling = false;
            if (!u.moved) {
                u.resetPosition();
                if (a) {
                    w = y.target;
                    while (w.nodeType != 1) {
                        w = w.parentNode
                    }
                    w.style.pointerEvents = "auto";
                    x = document.createEvent("MouseEvents");
                    x.initMouseEvent("click", true, true, v.view, 1, y.screenX, y.screenY, y.clientX, y.clientY, v.ctrlKey, v.altKey, v.shiftKey, v.metaKey, 0, null);
                    x._fake = true;
                    w.dispatchEvent(x)
                }
                return
            }
            if (!u.options.snap && q > 250) {
                u.resetPosition();
                return
            }
            if (u.options.momentum) {
                p = u.scrollX === true ? u.momentum(u.x - u.scrollStartX, q, u.options.bounce ? -u.x + u.scrollWidth / 5 : -u.x, u.options.bounce ? u.x + u.scrollerWidth - u.scrollWidth + u.scrollWidth / 5 : u.x + u.scrollerWidth - u.scrollWidth) : {
                    dist: 0,
                    time: 0
                };
                n = u.scrollY === true ? u.momentum(u.y - u.scrollStartY, q, u.options.bounce ? -u.y + u.scrollHeight / 5 : -u.y, u.options.bounce ? (u.maxScrollY < 0 ? u.y + u.scrollerHeight - u.scrollHeight : 0) + u.scrollHeight / 5 : u.y + u.scrollerHeight - u.scrollHeight) : {
                    dist: 0,
                    time: 0
                };
                o = Math.max(Math.max(p.time, n.time), 1);
                t = u.x + p.dist;
                s = u.y + n.dist
            }
            if (u.options.snap) {
                r = u.snap(t, s);
                t = r.x;
                s = r.y;
                o = Math.max(r.time, o)
            }
            u.scrollTo(t, s, o + "ms")
        },
        transitionEnd: function () {
            var n = this;
            document.removeEventListener("webkitTransitionEnd", n, false);
            n.resetPosition()
        },
        resetPosition: function () {
            var n = this,
                p = n.x,
                o = n.y;
            if (n.x >= 0) {
                p = 0
            } else {
                if (n.x < n.maxScrollX) {
                    p = n.maxScrollX
                }
            }
            if (n.y >= 0 || n.maxScrollY > 0) {
                o = 0
            } else {
                if (n.y < n.maxScrollY) {
                    o = n.maxScrollY
                }
            }
            if (p != n.x || o != n.y) {
                n.scrollTo(p, o)
            } else {
                if (n.moved) {
                    n.onScrollEnd();
                    n.moved = false
                }
                if (n.scrollBarX) {
                    n.scrollBarX.hide()
                }
                if (n.scrollBarY) {
                    n.scrollBarY.hide()
                }
            }
        },
        snap: function (n, q) {
            var o = this,
                p;
            if (o.directionX > 0) {
                n = Math.floor(n / o.scrollWidth)
            } else {
                if (o.directionX < 0) {
                    n = Math.ceil(n / o.scrollWidth)
                } else {
                    n = Math.round(n / o.scrollWidth)
                }
            }
            o.pageX = -n;
            n = n * o.scrollWidth;
            if (n > 0) {
                n = o.pageX = 0
            } else {
                if (n < o.maxScrollX) {
                    o.pageX = o.maxPageX;
                    n = o.maxScrollX
                }
            }
            if (o.directionY > 0) {
                q = Math.floor(q / o.scrollHeight)
            } else {
                if (o.directionY < 0) {
                    q = Math.ceil(q / o.scrollHeight)
                } else {
                    q = Math.round(q / o.scrollHeight)
                }
            }
            o.pageY = -q;
            q = q * o.scrollHeight;
            if (q > 0) {
                q = o.pageY = 0
            } else {
                if (q < o.maxScrollY) {
                    o.pageY = o.maxPageY;
                    q = o.maxScrollY
                }
            }
            p = Math.round(Math.max(Math.abs(o.x - n) / o.scrollWidth * 500, Math.abs(o.y - q) / o.scrollHeight * 500));
            return {
                x: n,
                y: q,
                time: p
            }
        },
        scrollTo: function (o, n, q) {
            var p = this;
            if (p.x == o && p.y == n) {
                p.resetPosition();
                return
            }
            p.moved = true;
            p.setTransitionTime(q || "350ms");
            p.setPosition(o, n);
            if (q === "0" || q == "0s" || q == "0ms") {
                p.resetPosition()
            } else {
                document.addEventListener("webkitTransitionEnd", p, false)
            }
        },
        scrollToPage: function (p, o, r) {
            var q = this,
                n;
            if (!q.options.snap) {
                q.pageX = -Math.round(q.x / q.scrollWidth);
                q.pageY = -Math.round(q.y / q.scrollHeight)
            }
            if (p == "next") {
                p = ++q.pageX
            } else {
                if (p == "prev") {
                    p = --q.pageX
                }
            }
            if (o == "next") {
                o = ++q.pageY
            } else {
                if (o == "prev") {
                    o = --q.pageY
                }
            }
            p = -p * q.scrollWidth;
            o = -o * q.scrollHeight;
            n = q.snap(p, o);
            p = n.x;
            o = n.y;
            q.scrollTo(p, o, r || "500ms")
        },
        scrollToElement: function (o, q) {
            o = typeof o == "object" ? o : this.element.querySelector(o);
            if (!o) {
                return
            }
            var p = this,
                n = p.scrollX ? -o.offsetLeft : 0,
                r = p.scrollY ? -o.offsetTop : 0;
            if (n >= 0) {
                n = 0
            } else {
                if (n < p.maxScrollX) {
                    n = p.maxScrollX
                }
            }
            if (r >= 0) {
                r = 0
            } else {
                if (r < p.maxScrollY) {
                    r = p.maxScrollY
                }
            }
            p.scrollTo(n, r, q)
        },
        momentum: function (u, o, s, n) {
            var r = 2.5,
                t = 1.2,
                p = Math.abs(u) / o * 1000,
                q = p * p / r / 1000,
                v = 0;
            if (u > 0 && q > s) {
                p = p * s / q / r;
                q = s
            } else {
                if (u < 0 && q > n) {
                    p = p * n / q / r;
                    q = n
                }
            }
            q = q * (u < 0 ? -1 : 1);
            v = p / t;
            return {
                dist: Math.round(q),
                time: Math.round(v)
            }
        },
        onScrollEnd: function () {},
        destroy: function (n) {
            var o = this;
            window.removeEventListener("onorientationchange" in window ? "orientationchange" : "resize", o, false);
            o.element.removeEventListener(h, o, false);
            o.element.removeEventListener(k, o, false);
            o.element.removeEventListener(g, o, false);
            document.removeEventListener("webkitTransitionEnd", o, false);
            if (o.options.checkDOMChanges) {
                o.element.removeEventListener("DOMSubtreeModified", o, false)
            }
            if (o.scrollBarX) {
                o.scrollBarX = o.scrollBarX.remove()
            }
            if (o.scrollBarY) {
                o.scrollBarY = o.scrollBarY.remove()
            }
            if (n) {
                o.wrapper.parentNode.removeChild(o.wrapper)
            }
            return null
        }
    };

    function m(n, s, r, o) {
        var q = this,
            p;
        q.dir = n;
        q.fade = r;
        q.shrink = o;
        q.uid = ++e;
        q.bar = document.createElement("div");
        p = "position:absolute;top:0;left:0;-webkit-transition-timing-function:cubic-bezier(0,0,0.25,1);pointer-events:none;-webkit-transition-duration:0;-webkit-transition-delay:0;-webkit-transition-property:-webkit-transform;z-index:10;background:rgba(0,0,0,0.5);-webkit-transform:" + j + "0,0" + b + ";" + (n == "horizontal" ? "-webkit-border-radius:3px 2px;min-width:6px;min-height:5px" : "-webkit-border-radius:2px 3px;min-width:5px;min-height:6px");
        q.bar.setAttribute("style", p);
        q.wrapper = document.createElement("div");
        p = "-webkit-mask:-webkit-canvas(scrollbar" + q.uid + q.dir + ");position:absolute;z-index:10;pointer-events:none;overflow:hidden;opacity:0;-webkit-transition-duration:" + (r ? "300ms" : "0") + ";-webkit-transition-delay:0;-webkit-transition-property:opacity;" + (q.dir == "horizontal" ? "bottom:2px;left:2px;right:7px;height:5px" : "top:2px;right:2px;bottom:7px;width:5px;");
        q.wrapper.setAttribute("style", p);
        q.wrapper.appendChild(q.bar);
        s.appendChild(q.wrapper)
    }
    m.prototype = {
        init: function (n, p) {
            var q = this,
                o;
            if (q.dir == "horizontal") {
                if (q.maxSize != q.wrapper.offsetWidth) {
                    q.maxSize = q.wrapper.offsetWidth;
                    o = document.getCSSCanvasContext("2d", "scrollbar" + q.uid + q.dir, q.maxSize, 5);
                    o.fillStyle = "rgb(0,0,0)";
                    o.beginPath();
                    o.arc(2.5, 2.5, 2.5, Math.PI / 2, -Math.PI / 2, false);
                    o.lineTo(q.maxSize - 2.5, 0);
                    o.arc(q.maxSize - 2.5, 2.5, 2.5, -Math.PI / 2, Math.PI / 2, false);
                    o.closePath();
                    o.fill()
                }
            } else {
                if (q.maxSize != q.wrapper.offsetHeight) {
                    q.maxSize = q.wrapper.offsetHeight;
                    o = document.getCSSCanvasContext("2d", "scrollbar" + q.uid + q.dir, 5, q.maxSize);
                    o.fillStyle = "rgb(0,0,0)";
                    o.beginPath();
                    o.arc(2.5, 2.5, 2.5, Math.PI, 0, false);
                    o.lineTo(5, q.maxSize - 2.5);
                    o.arc(2.5, q.maxSize - 2.5, 2.5, 0, Math.PI, false);
                    o.closePath();
                    o.fill()
                }
            }
            q.size = Math.max(Math.round(q.maxSize * q.maxSize / p), 6);
            q.maxScroll = q.maxSize - q.size;
            q.toWrapperProp = q.maxScroll / (n - p);
            q.bar.style[q.dir == "horizontal" ? "width" : "height"] = q.size + "px"
        },
        setPosition: function (o) {
            var n = this;
            if (n.wrapper.style.opacity != "1") {
                n.show()
            }
            o = Math.round(n.toWrapperProp * o);
            if (o < 0) {
                o = n.shrink ? o + o * 3 : 0;
                if (n.size + o < 7) {
                    o = -n.size + 6
                }
            } else {
                if (o > n.maxScroll) {
                    o = n.shrink ? o + (o - n.maxScroll) * 3 : n.maxScroll;
                    if (n.size + n.maxScroll - o < 7) {
                        o = n.size + n.maxScroll - 6
                    }
                }
            }
            o = n.dir == "horizontal" ? j + o + "px,0" + b : j + "0," + o + "px" + b;
            n.bar.style.webkitTransform = o
        },
        show: function () {
            if (f) {
                this.wrapper.style.webkitTransitionDelay = "0"
            }
            this.wrapper.style.opacity = "1"
        },
        hide: function () {
            if (f) {
                this.wrapper.style.webkitTransitionDelay = "350ms"
            }
            this.wrapper.style.opacity = "0"
        },
        remove: function () {
            this.wrapper.parentNode.removeChild(this.wrapper);
            return null
        }
    };
    var f = ("WebKitCSSMatrix" in window && "m11" in new WebKitCSSMatrix()),
        c = (/iphone/gi).test(navigator.appVersion),
        d = (/ipad/gi).test(navigator.appVersion),
        i = (/android/gi).test(navigator.appVersion),
        a = c || d || i,
        h = a ? "touchstart" : "mousedown",
        k = a ? "touchmove" : "mousemove",
        g = a ? "touchend" : "mouseup",
        j = "translate" + (f ? "3d(" : "("),
        b = f ? ",0)" : ")",
        e = 0;
    window.iScroll = l
})();
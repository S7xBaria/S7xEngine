#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <sys/time.h>
#include <time.h>
#include <dlfcn.h>
#include <jni.h>
static double sm=1.0;
static int ini=0;
static void isp(){if(ini)return;ini=1;const char*s=getenv("S7X_SPEED");if(s){double v=atof(s);if(v>0.001&&v<100.0)sm=v;}}
static double vsr=0.0,vsv=0.0;
static double as(double r){if(!ini)isp();if(sm==1.0)return r;if(vsr==0.0){vsr=r;vsv=r;}double el=r-vsr;return vsv+el*sm;}
extern "C" int clock_gettime(int c,struct timespec*t){static int(*rc)(int,struct timespec*)=0;if(!rc)rc=(int(*)(int,struct timespec*))dlsym(RTLD_NEXT,"clock_gettime");int r=rc(c,t);if(c==CLOCK_MONOTONIC||c==CLOCK_REALTIME){double n=t->tv_sec+t->tv_nsec/1e9;double m=as(n);t->tv_sec=(time_t)m;t->tv_nsec=(long)((m-t->tv_sec)*1e9);}return r;}
extern "C" int gettimeofday(struct timeval*t,void*z){static int(*rg)(struct timeval*,void*)=0;if(!rg)rg=(int(*)(struct timeval*,void*))dlsym(RTLD_NEXT,"gettimeofday");int r=rg(t,z);if(r==0&&t){double n=t->tv_sec+t->tv_usec/1e6;double m=as(n);t->tv_sec=(time_t)m;t->tv_usec=(long)((m-t->tv_sec)*1e6);}return r;}
extern "C" time_t time(time_t*t){static time_t(*rt)(time_t*)=0;if(!rt)rt=(time_t(*)(time_t*))dlsym(RTLD_NEXT,"time");time_t r=rt(t);double m=as((double)r);time_t rs=(time_t)m;if(t)*t=rs;return rs;}
extern "C" JNIEXPORT void JNICALL Java_com_s7x_engine_Speedhack_setSpeed(JNIEnv*e,jobject,jdouble s){if(s>0.001&&s<100.0)sm=s;}
extern "C" JNIEXPORT jdouble JNICALL Java_com_s7x_engine_Speedhack_getSpeed(JNIEnv*e,jobject){return sm;}

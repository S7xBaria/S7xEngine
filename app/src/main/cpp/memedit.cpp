#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>

static int om(int p,int f){char b[64];snprintf(b,64,"/proc/%d/mem",p);return open(b,f);}
static std::string rcl(int p){char b[64];snprintf(b,64,"/proc/%d/cmdline",p);FILE*f=fopen(b,"r");if(!f)return "";char buf[256];size_t n=fread(buf,1,255,f);fclose(f);if(!n)return "";buf[n]=0;for(size_t i=0;i<n;i++)if(!buf[i])buf[i]=' ';return std::string(buf);}

extern "C" JNIEXPORT jobjectArray JNICALL Java_com_s7x_engine_MemoryEngine_nativeListProcesses(JNIEnv*e,jobject){
std::vector<std::string>r;DIR*d=opendir("/proc");if(!d)return 0;struct dirent*en;
while((en=readdir(d))){if(en->d_type!=DT_DIR)continue;int p=atoi(en->d_name);if(p<=0)continue;
std::string n=rcl(p);if(n.empty())continue;char xp[64],lt[256]={0};snprintf(xp,64,"/proc/%d/exe",p);
ssize_t L=readlink(xp,lt,255);std::string a="x64";if(L>0&&strstr(lt,"app_process32"))a="x86";
char fl[512];snprintf(fl,512,"%d|%s|%s",p,n.c_str(),a.c_str());r.push_back(std::string(fl));}
closedir(d);jclass sc=e->FindClass("java/lang/String");jobjectArray ar=e->NewObjectArray(r.size(),sc,0);
for(size_t i=0;i<r.size();i++)e->SetObjectArrayElement(ar,i,e->NewStringUTF(r[i].c_str()));return ar;}

extern "C" JNIEXPORT jobjectArray JNICALL Java_com_s7x_engine_MemoryEngine_nativeGetMaps(JNIEnv*e,jobject,jint p){
char b[64];snprintf(b,64,"/proc/%d/maps",p);FILE*f=fopen(b,"r");if(!f)return 0;
std::vector<std::string>m;char l[512];while(fgets(l,512,f))m.push_back(std::string(l));fclose(f);
jclass sc=e->FindClass("java/lang/String");jobjectArray ar=e->NewObjectArray(m.size(),sc,0);
for(size_t i=0;i<m.size();i++)e->SetObjectArrayElement(ar,i,e->NewStringUTF(m[i].c_str()));return ar;}

extern "C" JNIEXPORT jint JNICALL Java_com_s7x_engine_MemoryEngine_nativeGetPidFromPackage(JNIEnv*e,jobject,jstring pn){
const char*p=e->GetStringUTFChars(pn,0);if(!p)return -1;char c[256];snprintf(c,256,"pidof %s",p);
FILE*f=popen(c,"r");if(!f){e->ReleaseStringUTFChars(pn,p);return -1;}int r=-1;
if(fscanf(f,"%d",&r)!=1)r=-1;pclose(f);e->ReleaseStringUTFChars(pn,p);return r;}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_s7x_engine_MemoryEngine_nativeScanValue(
JNIEnv*e,jobject,jint p,jlong s,jlong en,jstring vs,jint t,jint m){
const char*v=e->GetStringUTFChars(vs,0);if(!v)return 0;std::string vi(v);e->ReleaseStringUTFChars(vs,v);
int fd=om(p,O_RDONLY);if(fd<0)return 0;
int ts=(t==0)?1:(t==1)?2:(t==2)?4:(t==3)?8:(t==4)?4:8;
double t1=0,t2=0;bool ir=false;
try{if(m==3&&vi.find('-')!=std::string::npos){size_t q=vi.find('-');t1=std::stod(vi.substr(0,q));t2=std::stod(vi.substr(q+1));ir=true;}else t1=std::stod(vi);}
catch(...){close(fd);return 0;}
std::vector<jlong>r;const size_t ch=65536;std::vector<uint8_t>bf(ch);
for(jlong a=s;a<en;a+=(jlong)ch){size_t tr=(en-a)<(jlong)ch?(size_t)(en-a):ch;
ssize_t by=pread64(fd,bf.data(),tr,a);if(by<=0)continue;
for(ssize_t i=0;i<=by-ts;i++){double v=0;
if(t==0){int8_t x;memcpy(&x,bf.data()+i,1);v=x;}
else if(t==1){int16_t x;memcpy(&x,bf.data()+i,2);v=x;}
else if(t==2){int32_t x;memcpy(&x,bf.data()+i,4);v=x;}
else if(t==3){int64_t x;memcpy(&x,bf.data()+i,8);v=(double)x;}
else if(t==4){float x;memcpy(&x,bf.data()+i,4);v=x;}
else{double x;memcpy(&x,bf.data()+i,8);v=x;}
bool mt=false;
if(m==0)mt=(std::fabs(v-t1)<0.0001);else if(m==1)mt=(v>t1);else if(m==2)mt=(v<t1);else if(m==3&&ir)mt=(v>=t1&&v<=t2);
if(mt){r.push_back(a+i);if(r.size()>=100000)break;}}
if(r.size()>=100000)break;}
close(fd);jlongArray ar=e->NewLongArray(r.size());
if(r.size()>0)e->SetLongArrayRegion(ar,0,r.size(),(jlong*)r.data());return ar;}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_s7x_engine_MemoryEngine_nativeFilterValue(
JNIEnv*e,jobject,jint p,jlongArray aa,jstring vs,jint t,jint m){
if(!aa)return 0;jsize ln=e->GetArrayLength(aa);jlong*ad=e->GetLongArrayElements(aa,0);
const char*v=e->GetStringUTFChars(vs,0);std::string vi(v?v:"0");if(v)e->ReleaseStringUTFChars(vs,v);
int fd=om(p,O_RDONLY);if(fd<0){e->ReleaseLongArrayElements(aa,ad,JNI_ABORT);return 0;}
double tg=0;try{tg=std::stod(vi);}catch(...){}
std::vector<jlong>kp;
for(jsize i=0;i<ln;i++){double vv=0;bool rd=false;
if(t==0){int8_t x;if(pread64(fd,&x,1,ad[i])==1){vv=x;rd=true;}}
else if(t==1){int16_t x;if(pread64(fd,&x,2,ad[i])==2){vv=x;rd=true;}}
else if(t==2){int32_t x;if(pread64(fd,&x,4,ad[i])==4){vv=x;rd=true;}}
else if(t==3){int64_t x;if(pread64(fd,&x,8,ad[i])==8){vv=(double)x;rd=true;}}
else if(t==4){float x;if(pread64(fd,&x,4,ad[i])==4){vv=x;rd=true;}}
else{double x;if(pread64(fd,&x,8,ad[i])==8){vv=x;rd=true;}}
if(!rd)continue;bool ok=false;
if(m==0)ok=(std::fabs(vv-tg)<0.0001);else if(m==1)ok=(vv>tg);else if(m==2)ok=(vv<tg);
else if(m==3||m==4)ok=true;else if(m==5)ok=(vv>tg);else if(m==6)ok=(vv<tg);
if(ok)kp.push_back(ad[i]);}
close(fd);e->ReleaseLongArrayElements(aa,ad,JNI_ABORT);
jlongArray ar=e->NewLongArray(kp.size());
if(kp.size()>0)e->SetLongArrayRegion(ar,0,kp.size(),(jlong*)kp.data());return ar;}

extern "C" JNIEXPORT jboolean JNICALL Java_com_s7x_engine_MemoryEngine_nativeWriteValue(
JNIEnv*e,jobject,jint p,jlong a,jstring vs,jint t){
const char*v=e->GetStringUTFChars(vs,0);if(!v)return 0;std::string vl(v);e->ReleaseStringUTFChars(vs,v);
int fd=om(p,O_RDWR);if(fd<0)return 0;ssize_t n=0;
try{if(t==0){int8_t x=(int8_t)std::stoi(vl);n=pwrite64(fd,&x,1,a);}
else if(t==1){int16_t x=(int16_t)std::stoi(vl);n=pwrite64(fd,&x,2,a);}
else if(t==2){int32_t x=(int32_t)std::stol(vl);n=pwrite64(fd,&x,4,a);}
else if(t==3){int64_t x=(int64_t)std::stoll(vl);n=pwrite64(fd,&x,8,a);}
else if(t==4){float x=std::stof(vl);n=pwrite64(fd,&x,4,a);}
else{double x=std::stod(vl);n=pwrite64(fd,&x,8,a);}}
catch(...){close(fd);return 0;}
close(fd);return(n>0)?1:0;}

extern "C" JNIEXPORT jstring JNICALL Java_com_s7x_engine_MemoryEngine_nativeReadValueString(
JNIEnv*e,jobject,jint p,jlong a,jint t){
int fd=om(p,O_RDONLY);if(fd<0)return e->NewStringUTF("?");
char b[64];std::string r="?";
if(t==0){int8_t v;if(pread64(fd,&v,1,a)==1)snprintf(b,64,"%d",v),r=b;}
else if(t==1){int16_t v;if(pread64(fd,&v,2,a)==2)snprintf(b,64,"%d",v),r=b;}
else if(t==2){int32_t v;if(pread64(fd,&v,4,a)==4)snprintf(b,64,"%d",v),r=b;}
else if(t==3){int64_t v;if(pread64(fd,&v,8,a)==8)snprintf(b,64,"%lld",(long long)v),r=b;}
else if(t==4){float v;if(pread64(fd,&v,4,a)==4)snprintf(b,64,"%.4f",v),r=b;}
else{double v;if(pread64(fd,&v,8,a)==8)snprintf(b,64,"%.4f",v),r=b;}
close(fd);return e->NewStringUTF(r.c_str());}

extern "C" JNIEXPORT jlong JNICALL Java_com_s7x_engine_MemoryEngine_nativeReadValueLong(
JNIEnv*e,jobject,jint p,jlong a,jint t){
int fd=om(p,O_RDONLY);if(fd<0)return 0;int64_t r=0;
if(t==0){int8_t v;if(pread64(fd,&v,1,a)==1)r=v;}
else if(t==1){int16_t v;if(pread64(fd,&v,2,a)==2)r=v;}
else if(t==2){int32_t v;if(pread64(fd,&v,4,a)==4)r=v;}
else if(t==3){int64_t v;if(pread64(fd,&v,8,a)==8)r=v;}
else if(t==4){float v;if(pread64(fd,&v,4,a)==4)r=(int64_t)(v*10000);}
else{double v;if(pread64(fd,&v,8,a)==8)r=(int64_t)(v*10000);}
close(fd);return r;}

extern "C" JNIEXPORT jstring JNICALL Java_com_s7x_engine_MemoryEngine_nativeHexDump(
JNIEnv*e,jobject,jint p,jlong a,jint sz){
if(sz<=0||sz>4096)sz=256;int fd=om(p,O_RDONLY);if(fd<0)return e->NewStringUTF("ERR");
std::vector<uint8_t>bf(sz);ssize_t by=pread64(fd,bf.data(),sz,a);close(fd);
if(by<=0)return e->NewStringUTF("ERR");
std::string r;char l[128];
for(ssize_t i=0;i<by;i+=16){snprintf(l,128,"%016llX  ",(unsigned long long)(a+i));r+=l;
for(ssize_t j=0;j<16;j++){if(i+j<by){snprintf(l,128,"%02X ",bf[i+j]);r+=l;}else r+="   ";if(j==7)r+=" ";}
r+=" |";for(ssize_t j=0;j<16&&i+j<by;j++){char c=(char)bf[i+j];r+=(c>=32&&c<127)?c:'.';}r+="|\n";}
return e->NewStringUTF(r.c_str());}

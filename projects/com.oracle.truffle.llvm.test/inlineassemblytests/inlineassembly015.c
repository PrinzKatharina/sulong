int main() {
  int arg1 = -1;
  int shr = 0;
  __asm__("shrl $30, %%eax;" : "=a"(shr) : "a"(arg1));
  return shr;
}

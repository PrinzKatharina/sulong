int main() {
  int arg1 = -1;
  int sar = 0;
  __asm__("sarl $30, %%eax;" : "=a"(sar) : "a"(arg1));
  return sar;
}

int main() {
  int arg1 = 68;
  int mov = 0;
  __asm__("addl %%ecx, %%eax;" : "=a"(mov) : "c"(arg1));
  return mov;
}

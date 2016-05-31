int main() {
  int mov = 0;
  __asm__("addl $0xFF, %%eax;" : "=a"(mov));
  return mov;
}

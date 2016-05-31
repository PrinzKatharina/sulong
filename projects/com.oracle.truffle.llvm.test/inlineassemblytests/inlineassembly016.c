int main() {
  int arg1 = 43;
  int sal = 0;
  __asm__("sall $2, %%eax;" : "=a"(sal) : "a"(arg1));
  return sal;
}

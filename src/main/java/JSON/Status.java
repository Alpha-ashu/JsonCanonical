package JSON;

public class Status {
    // Updated Status class
        public int Length;
        public String StatusCode;
        public String Payer; // Added field for Payer value

        public Status() {
            this.Length = 0;
            this.StatusCode = "";
            this.Payer = null; // Default to null
        }
    }


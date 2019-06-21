# See section 9 of the NIST AIDA 2019 Evaluation Plan document for proper archive naming convention, formatting, and archive file types. #

This script is designed to help check if an archive is submit ready for M18 evaluation. 
"Submit ready" means that the archive is structured properly for the Next Century's automated AIF M18 batch validation process.

This script checks that the ttl files are located in a valid directory structure (e.g., not in the base directory when expected to be in a /NIST/ directory).

This script DOES NOT check for valid number of ttl files (e.g., will not find anything wrong with a TA2 archive with more than 1 ttl in a /NIST/ directory).

This script DOES NOT check that files in the /NIST/ directory are RESTRICTED AIF and that the files in the /INTER-TA/ directory are standard AIF, and so on. 

This script DOES NOT do a complete check against all of the submission archives naming rules. However, it checks for the proper submission file name extension and the number of periods in the submission file name.

### To run: ###

    python3 aida_m18_submission_checker.py <path/to/archive>
    or
    python aida_m18_submission_checker.py <path/to/archive>
    
    
    
